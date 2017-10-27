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

import java.util.Map.Entry;
import java.util.HashMap;

public class SFTPSession {
    private static SFTPSession sshSession = null;
    //Keep a cached Session ( = connection) per server
    private HashMap<Credential, Session> sessions;
    private HashMap<Session, Integer> usedSessions; // keep used session to avoid deconnection while, for example, a sftp channel is being used
    public SFTPSession(){
        sessions = new HashMap<Credential, Session>();
        usedSessions = new HashMap<>();
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
                keepSessionConnected(channel);
                return channel;
            } catch (JSchException e) {
                //channel isn't openable, we have to reset the session !
                removeSession(cred);
                Session session2 = getSession(cred);
                if(session2 !=null){
                    try {

                        Channel channel;
                        channel = session2.openChannel("sftp");
                        channel.connect();
                        keepSessionConnected(channel);
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

    //issue there when a session is disconnnected when a channel is being used :|
    private synchronized void keepSessionConnected(Channel channel){
        try {
            Session session = channel.getSession();
            Integer nSessions = usedSessions.get(session);
            if(nSessions == null) nSessions = 0;

            usedSessions.put(session, nSessions + 1);
        } catch (JSchException e) {
            e.printStackTrace();
        }

    }

    public synchronized void releaseSession(Channel channel) {
        try {

            Session session = channel.getSession();
            boolean contains = usedSessions.get(session) != null;
            //TODO: What do we do when it isn't there?
            //This should be an error case
            if(contains){
                if(usedSessions.get(session)<=1){
                    usedSessions.remove(session);
		    //If the session is no longer used, and this isn't our cached session for this server
		    //It means it won't ever be used again, so clear it.
		    //This means that we usually keep always one connection open
                    if(!sessions.values().contains(session))
                        channel.getSession().disconnect();
                }
                else{
                    usedSessions.put(session, usedSessions.get(session) - 1);
                }
            }
        } catch (JSchException e) {
            e.printStackTrace();
        }

    }

    public synchronized void removeSession(Uri cred){

        for(Entry<Credential, Session> e : sessions.entrySet()){
            Uri uri = Uri.parse(e.getKey().getUriString());
            if(uri.getHost().equals(cred.getHost())&&uri.getPort()==cred.getPort()){
                boolean doNotDisconnect = usedSessions.get(e.getValue()) != null;
                if(!doNotDisconnect) {
                    e.getValue().disconnect();
                }
                sessions.remove(e.getKey());
            }
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
        if(cred!=null){
            password= cred.getPassword();
            username = cred.getUsername();
        }

        Session session = sessions.get(cred);
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
            sessions.put(cred, session);
            return session;
        } catch (JSchException e) {
            // TODO Auto-generated catch block
            throw e;

        }
        

    }
}
