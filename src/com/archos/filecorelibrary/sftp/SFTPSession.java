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

import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

public class SFTPSession {
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
        Session session = getSession(cred);
        if(session !=null){
            try {
                Channel channel = session.openChannel("sftp");
                channel.connect();
                acquireSession(channel);
                return channel;
            } catch (JSchException e) {
                //channel isn't openable, we have to reset the session !
                removeSession(cred);
                Session session2 = getSession(cred);
                if (session2 != null) {
                    try {

                        Channel channel;
                        channel = session2.openChannel("sftp");
                        channel.connect();
                        acquireSession(channel);
                        return channel;
                    } catch (JSchException e1) {
                        // TODO Auto-generated catch block
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
            HashSet<Channel> channels = usedSessions.get(session);
            if(channels == null) {
                channels = new HashSet<>();
                usedSessions.put(session, channels);
            }
            channels.add(channel);
        } catch (JSchException e) {
        }

    }

    public synchronized void releaseSession(Channel channel) {
        try {
            Session session = channel.getSession();
            HashSet<Channel> channels = usedSessions.get(session);
            boolean deleted = channels.remove(channel);
            //We already deleted this channel before
            if(!deleted) return;
            if(channels.isEmpty()) {
                //If this is our current session for this credential, keep it
                if(currentSessions.values().contains(session)) return;
                session.disconnect();
                usedSessions.remove(session);
            }
        } catch (JSchException e) {
            e.printStackTrace();
        }

    }

    /*
    This is called by long-standing calls which open/close many channels
    To keep the session alive.
    For instance, scraping will ls / then ls /data, which would normally close the sftp connection
    on every request
     */
    public synchronized void removeSession(Uri cred) {
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
                s.disconnect();
            }

            currentSessions.remove(c);
        }
    }


    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }


    public synchronized Session getSession(Uri path) throws JSchException{
        String username="anonymous";

        String password = "";
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if(cred==null){
            cred = new Credential("anonymous","",buildKeyFromUri(path).toString(), true);

        }

        password= cred.getPassword();
        username = cred.getUsername();

        Session session = currentSessions.get(cred);
        if(session!=null){
            if(!session.isConnected())
                try {
                    session.connect();
                } catch (JSchException e1) {
                    removeSession(path);
                    return getSession(path);
                }
            return session;
        }

        JSch jsch=new JSch();
        try {
            session = jsch.getSession(username, path.getHost(), path.getPort());
            session.setPassword(password);
            java.util.Properties config = new java.util.Properties();
            config.put("StrictHostKeyChecking", "no");
            session.setConfig(config);
            session.connect();
            currentSessions.put(cred, session);
            return session;
        } catch (JSchException e) {
            // TODO Auto-generated catch block
            throw e;

        }
        

    }
}
