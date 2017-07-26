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

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

public class SftpFileEditor  extends FileEditor{

    public SftpFileEditor(Uri uri) {
        super(uri);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ChannelSftp channelSftp = (ChannelSftp)channel;
            channelSftp.mkdir(mUri.getPath());
            channel.disconnect();
            return true;
        }
        catch (SftpException e) {

        } catch (JSchException e) {
            e.printStackTrace();
        } finally {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
        }
        return false;
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException, JSchException, SftpException {
        Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        return ((ChannelSftp)channel).get(mUri.getPath());
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        return ((ChannelSftp)channel).get(mUri.getPath(), null, from);
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException, JSchException, SftpException {
        Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        return ((ChannelSftp)channel).put(mUri.getPath());
    }

    @Override
    public void delete() throws Exception {
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rm(mUri.getPath());

        } catch (JSchException e) {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
            if(e.getCause() instanceof java.net.UnknownHostException)
                throw new UnknownHostException();
            else
                throw new AuthenticationException();
        } catch (SftpException e) {
            throw new Exception("permission");
        } finally {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
        }


    }
    @Override
    public boolean rename(String newName){
        Channel channel = null;
        try {
             channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
            channel.disconnect();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        if(!mUri.getScheme().equals(uri.getScheme())|| !mUri.getHost().equals(uri.getHost())||mUri.getPort()!=uri.getPort())
            return false;
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rename(mUri.getPath(),uri.getPath());
            channel.disconnect();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
        }finally {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
        }
        return false;
    }


    @Override
    public boolean exists() {
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            SftpATTRS attrs =  ((ChannelSftp)channel).stat(mUri.getPath());
            channel.disconnect();
            return attrs !=null;
        } catch (Exception e) {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
            e.printStackTrace();
        }finally {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
        }
        return false;
    }


}
