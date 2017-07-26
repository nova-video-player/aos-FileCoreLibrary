#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <errno.h>
#include <sys/socket.h>
#include <linux/sockios.h>
#include <netdb.h>
#include <net/if.h>
#include <netinet/ip.h>
#include <netinet/ip_icmp.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#include <linux/types.h>
#include <linux/errqueue.h>

#ifdef ANDROID
#include <linux/icmp.h>
#endif

#if BYTE_ORDER == LITTLE_ENDIAN
# define ODDBYTE(v)	(v)
#elif BYTE_ORDER == BIG_ENDIAN
# define ODDBYTE(v)	((u_short)(v) << 8)
#else
# define ODDBYTE(v)	htons((u_short)(v) << 8)
#endif

u_short
in_cksum(const u_short *addr, register int len, u_short csum)
{
	register int nleft = len;
	const u_short *w = addr;
	register u_short answer;
	register int sum = csum;

	/*
	 *  Our algorithm is simple, using a 32 bit accumulator (sum),
	 *  we add sequential 16 bit words to it, and at the end, fold
	 *  back all the carry bits from the top 16 bits into the lower
	 *  16 bits.
	 */
	while (nleft > 1)  {
		sum += *w++;
		nleft -= 2;
	}

	/* mop up an odd byte, if necessary */
	if (nleft == 1)
		sum += ODDBYTE(*(u_char *)w); /* le16toh() may be unavailable on old systems */

	/*
	 * add back carry outs from top 16 bits to low 16 bits
	 */
	sum = (sum >> 16) + (sum & 0xffff);	/* add hi 16 to low 16 */
	sum += (sum >> 16);			/* add carry */
	answer = ~sum;				/* truncate to 16 bits */
	return (answer);
}

static struct {
  struct hostent *h;
  struct sockaddr_in source;
  struct sockaddr_in destin;
  int icmp_sock;
  u_char outpack[0x100];
  int ttl;
  int cmsg_len;
} p;

static struct {
  struct cmsghdr cm;
  struct in_pktinfo ipi;
} cmsg = { {sizeof(struct cmsghdr) + sizeof(struct in_pktinfo), SOL_IP, IP_PKTINFO}, {0, }};


static int receive_probe_response() {
  int res;
  char cbuf[512];
  struct iovec  iov;
  struct msghdr msg;
  struct cmsghdr *cmsg;
  struct sock_extended_err *e;
  struct icmphdr icmph;
  struct sockaddr_in target;
  
  iov.iov_base = &icmph;
  iov.iov_len = sizeof(icmph);
  msg.msg_name = (void*)&target;
  msg.msg_namelen = sizeof(target);
  msg.msg_iov = &iov;
  msg.msg_iovlen = 1;
  msg.msg_flags = 0;
  msg.msg_control = cbuf;
  msg.msg_controllen = sizeof(cbuf);

  fd_set rfds;
  struct timeval tv;
  int retval;
  
  FD_ZERO(&rfds);
  FD_SET(p.icmp_sock, &rfds);

  /* Wait up to one second. */
  tv.tv_sec = 1;
  tv.tv_usec = 0;
  
  retval = select(p.icmp_sock+1, &rfds, NULL, NULL, &tv);
  /* Don't rely on the value of tv now! */
  
  if (retval == -1)
    goto out;
  if (!retval)
    goto out;
  
  res = recvmsg(p.icmp_sock, &msg, 0);
  if (res < 0)
    if (errno != EHOSTUNREACH) 
      goto out;
  
  FD_ZERO(&rfds);
  FD_SET(p.icmp_sock, &rfds);
  tv.tv_sec = 1;
  tv.tv_usec = 0;
  
  retval = select(p.icmp_sock+1, &rfds, NULL, NULL, &tv);
  
  if (retval == -1)
    goto out;
  if (!retval)
    goto out;

  res = recvmsg(p.icmp_sock, &msg, MSG_ERRQUEUE);
  if (res < 0)
    goto out;
  
  e = NULL;
  for (cmsg = CMSG_FIRSTHDR(&msg); cmsg; cmsg = CMSG_NXTHDR(&msg, cmsg)) {
    if (cmsg->cmsg_level == SOL_IP) {
      if (cmsg->cmsg_type == IP_RECVERR)
	e = (struct sock_extended_err *)CMSG_DATA(cmsg);
    }
  }
  
  if (e && e->ee_origin == SO_EE_ORIGIN_ICMP) {
    struct sockaddr_in *sin = (struct sockaddr_in*)(e+1);

    if (res < sizeof(icmph) ||
	target.sin_addr.s_addr != p.destin.sin_addr.s_addr ||
	icmph.type != ICMP_ECHO) {
      //not the droids we are looking for
      goto out;
    } 
 

  if (e && e->ee_type == ICMP_TIME_EXCEEDED
      && e->ee_code == ICMP_EXC_TTL)
    return sin->sin_addr.s_addr;
  }
  
 out:
  return -1;
}
  
static int send_probe() {
  struct icmphdr *icp;
  int icp_size = 64;
  int sent_size;
  int seq_id = 1;
  int flags = 0;
  
  icp = (struct icmphdr *) p.outpack;
  icp->type = ICMP_ECHO;
  icp->code = 0;
  icp->checksum = 0;
  icp->un.echo.sequence = htons(seq_id);
  icp->un.echo.id = 42;
  
  memset(icp+1, 0, sizeof(struct timeval));    
  icp->checksum = in_cksum((u_short *)icp, icp_size, 0);
  
  do {
    static struct iovec iov = {p.outpack, 0};
    static struct msghdr m = { &p.destin, sizeof(p.destin),
			       &iov, 1, &cmsg, 0, 0 };
    m.msg_controllen = p.cmsg_len;
    iov.iov_len = icp_size;
    
    sent_size = sendmsg(p.icmp_sock, &m, flags);
  } while (0);
  
  return (icp_size == sent_size ? 0 : sent_size);
}

/*********************************************\
 *
 * send an icp request to an ipv4 server with
 * a specified small ttl 
 * designed for nat layers discovery in LANs
 * were icmp echo will fail before attaining 
 * target and will give current hop GW
 *
\*********************************************/
int get_ttl_ip(char* target, int ttl) {
  int result;

  p.h = gethostbyname(target);
  memcpy(&p.destin.sin_addr, p.h->h_addr, 4);
  p.ttl = ttl;
  p.cmsg_len = sizeof(cmsg);

  p.source.sin_family = AF_INET;
  p.destin.sin_family = AF_INET;

  p.icmp_sock = socket(AF_INET, SOCK_DGRAM, IPPROTO_ICMP);
  if (p.icmp_sock < 0)
    return -1;

  int hold = 1;
  if (setsockopt(p.icmp_sock, SOL_IP, IP_RECVERR, (char *)&hold, sizeof(hold)) == -1) {
    result = -1;
    goto out;
  }
  if (setsockopt(p.icmp_sock, SOL_IP, IP_RECVTTL, (char *)&hold, sizeof(hold)) == -1) {
    result = -1;
    goto out;
  }

  if (setsockopt(p.icmp_sock, IPPROTO_IP, IP_TTL, &p.ttl, sizeof(p.ttl)) == -1) { 
    result = -1;
    goto out;
  }

  result = send_probe();
  if (result != 0) 
    goto out;;
  
  result = receive_probe_response();

 out:
  close(p.icmp_sock);
  
  return result;
}

int get_local_ip(char *target) {
  p.h = gethostbyname(target);
  memcpy(&p.destin.sin_addr, p.h->h_addr, 4);
  p.cmsg_len = sizeof(cmsg);

  p.source.sin_family = AF_INET;
  p.destin.sin_family = AF_INET;
  
  socklen_t alen;
  struct sockaddr_in dst = p.destin;
  int probe_fd = socket(AF_INET, SOCK_DGRAM, 0);
  if (probe_fd < 0)
    goto out;
  dst.sin_port = htons(1025);
  if (connect(probe_fd, (struct sockaddr*)&dst, sizeof(dst)) == -1) {
    close(probe_fd);
    goto out;
  }
  alen = sizeof(p.source);
  if (getsockname(probe_fd, (struct sockaddr*)&p.source, &alen) == -1) {
    close(probe_fd);
    goto out;
  }

  close(probe_fd);
  return p.source.sin_addr.s_addr;
  
  out:
  return -1;
  
}

#ifndef ANDROID

int main(int argc, char** argv) {
  struct in_addr ip;
  
  ip.s_addr = get_local_ip("8.8.8.8");
  printf("local ip is %s\n", inet_ntoa(ip));
  
  ip.s_addr = get_ttl_ip("8.8.8.8", 1);
  printf("gw ip with tt 1 is %s\n", inet_ntoa(ip));
  
  ip.s_addr = get_ttl_ip("8.8.8.8", 2);
  printf("gw ip with tt 2 is %s\n", inet_ntoa(ip));
  
  return 0;
}

#endif
