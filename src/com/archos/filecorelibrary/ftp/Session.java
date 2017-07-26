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

package com.archos.filecorelibrary.ftp;

import java.io.IOException;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map.Entry;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.net.ftp.FTPSClient;

import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;

public class Session {
    private static final String TAG = "ftp.Session";
    private static Session sSession = null;
    private final HashMap<Credential, FTPClient> ftpsClients;

    private HashMap <Credential, FTPClient> ftpClients;
    public Session(){
        ftpClients = new HashMap<Credential, FTPClient>();
        ftpsClients = new HashMap<Credential, FTPClient>();
    }


    public void removeFTPClient(Uri cred){
        for(Entry<Credential,FTPClient> e : ftpClients.entrySet()){
            if((e.getKey()).getUriString().equals(cred.toString())){
                ftpClients.remove(e.getKey()); 
            }
        }
    }

    public FTPClient getNewFTPClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException{

        // Use default port if not set
        int port = path.getPort();
        if (port<0) {
            port = 21; // default port
        }

        String username="anonymous"; // default user
        String password = ""; // default password

        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if(cred!=null){
            password= cred.getPassword();
            username = cred.getUsername();
        }
        FTPClient ftp= new FTPClient();

        //try to connect
        ftp.connect(path.getHost(), port);
        //login to 	server
        if(!ftp.login(username, password))
        {
            ftp.logout();
            throw new AuthenticationException();
        }
        if(mode>=0){
            ftp.setFileType(mode);

        }
        int reply = ftp.getReplyCode();
        //FTPReply stores a set of constants for FTP reply codes. 
        if (!FTPReply.isPositiveCompletion(reply))
        {
            try {
                ftp.disconnect();
            } catch (IOException e) {
                throw e;
            }
            return null;
        }
        //enter passive mode
        ftp.enterLocalPassiveMode();

        return ftp;
    }

    public FTPClient getFTPClient(Uri uri) throws SocketException, IOException, AuthenticationException{
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if(cred==null){
            cred = new Credential("anonymous","", buildKeyFromUri(uri).toString(), true);
        }
        FTPClient ftpclient = ftpClients.get(cred);
        if (ftpclient!=null && ftpclient.isConnected()){
            return ftpclient;
        }
        // Not previous session found, open a new one
        Log.d(TAG, "create new ftp session for "+uri);
        FTPClient ftp = getNewFTPClient(uri,FTP.BINARY_FILE_TYPE);
        if(ftp==null)
            return null;
        Uri key = buildKeyFromUri(uri);
        Log.d(TAG, "new ftp session created with key "+key);
        ftpClients.put(cred, ftp);
        return ftp;
    }
    public FTPClient getFTPSClient(Uri uri) throws SocketException, IOException, AuthenticationException{
        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(uri.toString());
        if(cred==null){
            cred = new Credential("anonymous","", buildKeyFromUri(uri).toString(), true);
        }
        FTPClient ftpclient = ftpsClients.get(cred);
        if (ftpclient!=null && ftpclient.isConnected()){
            return ftpclient;
        }
        // Not previous session found, open a new one
        Log.d(TAG, "create new ftp session for "+uri);
        FTPClient ftp = getNewFTPSClient(uri, FTP.BINARY_FILE_TYPE);
        if(ftp==null)
            return null;
        Uri key = buildKeyFromUri(uri);
        Log.d(TAG, "new ftp session created with key "+key);
        ftpsClients.put(cred, ftp);
        return ftp;
    }
    private Uri buildKeyFromUri(Uri uri) {
        // We use the Uri without the path segment as key: for example, "ftp://blabla.com:21/toto/titi" gives a "ftp://blabla.com:21" key
        return uri.buildUpon().path("").build();
    }

    public static Session getInstance(){
        if(sSession==null)
            sSession= new Session();
        return sSession;
    }

    public FTPClient getNewFTPSClient(Uri path, int mode) throws SocketException, IOException, AuthenticationException{

        // Use default port if not set
        int port = path.getPort();
        if (port<0) {
            port = 21; // default port
        }

        String username="anonymous"; // default user
        String password = ""; // default password

        NetworkCredentialsDatabase database = NetworkCredentialsDatabase.getInstance();
        Credential cred = database.getCredential(path.toString());
        if(cred!=null){
            password= cred.getPassword();
            username = cred.getUsername();
        }
        FTPSClient ftp= new FTPSClient("TLS", false);
        //try to connect
        ftp.connect(path.getHost(), port);

        //login to 	server
        if(!ftp.login(username, password))
        {

            ftp.logout();
            throw new AuthenticationException();
        }
        if(mode>=0){
            ftp.setFileType(mode);

        }
        int reply = ftp.getReplyCode();
        //FTPReply stores a set of constants for FTP reply codes.
        if (!FTPReply.isPositiveCompletion(reply))
        {
            try {
                ftp.disconnect();
            } catch (IOException e) {
                throw e;
            }
            return null;
        }
        //enter passive mode
        ftp.enterLocalPassiveMode();
        // Set protection buffer size
        ftp.execPBSZ(0);
        // Set data channel protection to private
        ftp.execPROT("P");

        return ftp;
    }
}
