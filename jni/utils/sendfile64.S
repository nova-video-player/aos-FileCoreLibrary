
#define __NR_sendfile64 239

	  .text


@ ssize_t sendfile64(int out_fd, int in_fd, loff_t *offset, size_t count)

	  .global sendfile64
sendfile64:
	  push    {r4-r8,lr}
	  ldr     r7, =__NR_sendfile64
	  swi     #0
	  pop     {r4-r8,pc}
