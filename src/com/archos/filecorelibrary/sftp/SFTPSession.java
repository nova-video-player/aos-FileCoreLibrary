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
import com.archos.filecorelibrary.ConnectionLocks;

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

    private final ConnectionLocks connectionLocks = new ConnectionLocks();
    private static SFTPSession sshSession = null;
    //Keep a cached Session ( = connection) per server
    private ConcurrentHashMap<Credential, Session> currentSessions;
    private ConcurrentHashMap<Session, HashSet<Channel>> usedSessions; // keep used session to avoid deconnection while, for example, a sftp channel is being used
    public SFTPSession(){
        currentSessions = new ConcurrentHashMap<>();
        usedSessions = new ConcurrentHashMap<>();
    }

	public static synchronized SFTPSession getInstance(){
		if(sshSession==null)
			sshSession= new SFTPSession();
		return sshSession;
	}

    public Channel getSFTPChannel(Uri cred) throws JSchException {
        synchronized (connectionLocks.forUri(cred)) { return getSFTPChannelLocked(cred); }
    }

    private Channel getSFTPChannelLocked(Uri cred) throws JSchException {
        for (int attempt = 0; attempt < 2; attempt++) {
            Channel channel = null;
            boolean acquired = false;
            try {
                channel = getSession(cred).openChannel("sftp");
                channel.connect();
                acquireSession(channel);
                acquired = true;
                return channel;
            } catch (JSchException failure) {
                removeSession(cred);
                if (attempt == 1) throw failure;
            } finally {
                if (!acquired && channel != null) channel.disconnect();
            }
        }
        throw new JSchException("Unable to open SFTP channel");
    }

    private void acquireSession(Channel channel){
        try {
            Session session = channel.getSession();
            synchronized (session) {
                if (log.isTraceEnabled()) log.trace("acquireSession: acquiring channel {} for session {}", channel, session);
                HashSet<Channel> channels = usedSessions.get(session);
                if(channels == null) {
                    channels = new HashSet<>();
                    usedSessions.put(session, channels);
                }
                channels.add(channel);
            }
        } catch (JSchException e) {
            log.warn("acquireSession: failed to get session for channel {}", channel, e);
        }
    }

    public void releaseSession(Channel channel) {
        try {
            Session session = channel.getSession();
            synchronized (session) {
                if (log.isTraceEnabled()) log.trace("releaseSession: releasing channel {} for session {}", channel, session);
                HashSet<Channel> channels = usedSessions.get(session);
                // A failed acquisition or repeated close need not have a usage entry.
                if (channels == null || !channels.remove(channel)) return;
                if(channels.isEmpty()) {
                    usedSessions.remove(session);
                    // Keep the cached connection, but no empty usage entry.
                    if(currentSessions.values().contains(session)) return;
                    if (log.isDebugEnabled()) log.debug("releaseSession: no more channels in use, disconnecting session {}", session);
                    session.disconnect();
                    usedSessions.remove(session);
                }
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
    public void removeSession(Uri cred) {
        synchronized (connectionLocks.forUri(cred)) {
            for (Credential c : currentSessions.keySet()) {
                Uri uri = Uri.parse(c.getUriString());
                if (connectionLocks.forUri(uri) != connectionLocks.forUri(cred)) continue;
                Session session = currentSessions.get(c);
                if (session == null) continue;
                synchronized (session) {
                    currentSessions.remove(c, session);
                    HashSet<Channel> channels = usedSessions.get(session);
                    if (channels == null || channels.isEmpty()) {
                        usedSessions.remove(session);
                        session.disconnect();
                    }
                    // Active retired sessions are closed by their last owner.
                }
            }
        }
    }

    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }

    public Session getSession(Uri path) throws JSchException {
        synchronized (connectionLocks.forUri(path)) { return getSessionLocked(path); }
    }

    @SuppressWarnings("deprecation") // session.setPassword(String): legacy JSch API
    private Session getSessionLocked(Uri path) throws JSchException{
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
            if (session != null) session.disconnect();
            log.warn("getSession: failed to open new session for {}", path, e);
            throw e;

        }
    }
}
