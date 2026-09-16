// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.filecorelibrary.sftp;

import android.net.Uri;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class SFTPSession {

    private static final Logger log = LoggerFactory.getLogger(SFTPSession.class);

    // jsch defaults each pipelined SFTP READ request to a 32KB local packet size, which caps
    // achievable throughput regardless of pipelining depth. Raise it so that each of the up to
    // 16 in-flight requests (see ChannelSftp's request_max ramp-up) can carry more data per
    // round trip; the local window is scaled up to match so it isn't the new bottleneck.
    // Servers that only support shorter reads still work since jsch detects short reads and
    // re-requests the remainder.
    private static final int SFTP_LOCAL_PACKET_SIZE = 64 * 1024;
    private static final int SFTP_LOCAL_WINDOW_SIZE_MAX = 64 * SFTP_LOCAL_PACKET_SIZE;

    private static SFTPSession sshSession = null;
    //Keep a cached Session ( = connection) per server
    private ConcurrentHashMap<Credential, Session> currentSessions;
    private ConcurrentHashMap<Session, HashSet<Channel>> usedSessions; // keep used session to avoid deconnection while, for example, a sftp channel is being used
    public SFTPSession(){
        currentSessions = new ConcurrentHashMap<>();
        usedSessions = new ConcurrentHashMap<>();
    }

	public static SFTPSession getInstance(){
		if(sshSession==null)
			sshSession= new SFTPSession();
		return sshSession;
	}

	public synchronized Channel getSFTPChannel(Uri cred) throws JSchException{
        if (log.isDebugEnabled()) log.debug("getSFTPChannel: opening sftp channel for {}", cred);
        Session session = getSession(cred);
        if(session !=null){
            try {
                Channel channel = session.openChannel("sftp");
                channel.setLocalPacketSize(SFTP_LOCAL_PACKET_SIZE);
                channel.setLocalWindowSizeMax(SFTP_LOCAL_WINDOW_SIZE_MAX);
                channel.connect();
                acquireSession(channel);
                return channel;
            } catch (JSchException e) {
                //channel isn't openable, we have to reset the session !
                log.warn("getSFTPChannel: failed to open channel for {}, resetting session and retrying", cred, e);
                removeSession(cred);
                Session session2 = getSession(cred);
                if (session2 != null) {
                    try {

                        Channel channel;
                        channel = session2.openChannel("sftp");
                        channel.setLocalPacketSize(SFTP_LOCAL_PACKET_SIZE);
                        channel.setLocalWindowSizeMax(SFTP_LOCAL_WINDOW_SIZE_MAX);
                        channel.connect();
                        acquireSession(channel);
                        return channel;
                    } catch (JSchException e1) {
                        log.warn("getSFTPChannel: retry failed for {}", cred, e1);
                        throw e1;
                    }
                }
            }
        }
        return null;
    }

    private synchronized void acquireSession(Channel channel){
        try {
            Session session = channel.getSession();
            if (log.isTraceEnabled()) log.trace("acquireSession: acquiring channel {} for session {}", channel, session);
            HashSet<Channel> channels = usedSessions.get(session);
            if(channels == null) {
                channels = new HashSet<>();
                usedSessions.put(session, channels);
            }
            channels.add(channel);
        } catch (JSchException e) {
            log.warn("acquireSession: failed to get session for channel {}", channel, e);
        }
    }

    public synchronized void releaseSession(Channel channel) {
        try {
            Session session = channel.getSession();
            if (log.isTraceEnabled()) log.trace("releaseSession: releasing channel {} for session {}", channel, session);
            HashSet<Channel> channels = usedSessions.get(session);
            boolean deleted = channels.remove(channel);
            //We already deleted this channel before
            if(!deleted) return;
            if(channels.isEmpty()) {
                //If this is our current session for this credential, keep it
                if(currentSessions.values().contains(session)) return;
                if (log.isDebugEnabled()) log.debug("releaseSession: no more channels in use, disconnecting session {}", session);
                session.disconnect();
                usedSessions.remove(session);
            }
        } catch (Exception e) {
            log.warn("releaseSession: failed to release channel {}", channel, e);
        }
    }

    /*
    This is called by long-standing calls which open/close many channels
    To keep the session alive.
    For instance, scraping will ls / then ls /data, which would normally close the sftp connection
    on every request
     */
    public synchronized void removeSession(Uri cred) {
        if (log.isDebugEnabled()) log.debug("removeSession: removing session(s) for {}", cred);
        for(Credential c : currentSessions.keySet()){
            Uri uri = Uri.parse(c.getUriString());
            if(!uri.getHost().equals(cred.getHost()) || uri.getPort()!=cred.getPort())
                continue;
            Session s = currentSessions.get(c);
            boolean doNotDisconnect = usedSessions.get(s) != null;
            //If doNotDisconnect is true, it means there are still channels opened
            //Since we are removing this session from currentSessions
            //The session will be disconnected in releaseChannel
            if(!doNotDisconnect) {
                if (log.isTraceEnabled()) log.trace("removeSession: disconnecting session {} for {}", s, c);
                s.disconnect();
            }
            currentSessions.remove(c);
        }
    }

    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }

    @SuppressWarnings("deprecation") // session.setPassword(String): legacy JSch API
    public synchronized Session getSession(Uri path) throws JSchException{
        String username="anonymous";

        String password = "";
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if(cred==null){
            cred = new Credential("anonymous","",buildKeyFromUri(path).toString(),"",true);
        }
        password= cred.getPassword();
        username = cred.getUsername();
        Session session = currentSessions.get(cred);
        if(session!=null){
            if(!session.isConnected())
                try {
                    if (log.isDebugEnabled()) log.debug("getSession: reconnecting stale session for {}", path);
                    session.connect();
                } catch (JSchException e1) {
                    log.warn("getSession: failed to reconnect stale session for {}, removing and retrying", path, e1);
                    removeSession(path);
                    return getSession(path);
                }
            else if (log.isTraceEnabled()) log.trace("getSession: reusing session for {}", path);
            return session;
        }
        JSch jsch=new JSch();
        int port = path.getPort();
        if (port < 0) {
            port = 22;
        }
        try {
            if (log.isDebugEnabled()) log.debug("getSession: opening new session for {}@{}:{}", username, path.getHost(), port);
            session = jsch.getSession(username, path.getHost(), port);
            session.setPassword(password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            currentSessions.put(cred, session);
            return session;
        } catch (JSchException e) {
            log.warn("getSession: failed to open new session for {}", path, e);
            throw e;

        }
    }
}
