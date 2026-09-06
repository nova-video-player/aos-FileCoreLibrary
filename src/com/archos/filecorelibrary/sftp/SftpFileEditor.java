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
import android.util.Log;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.AuthenticationException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.UnknownHostException;

public class SftpFileEditor  extends FileEditor{

    private static final Logger log = LoggerFactory.getLogger(SftpFileEditor.class);

    private static final String TAG = "SftpFileEditor";
    private static final boolean DBG = false;

    public SftpFileEditor(Uri uri) {
        super(uri);
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        if (log.isDebugEnabled()) log.debug("mkdir: {}", mUri.getPath());
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ChannelSftp channelSftp = (ChannelSftp)channel;
            channelSftp.mkdir(mUri.getPath());
            channel.disconnect();
            SFTPSession.getInstance().releaseSession(channel);
            return true;
        }
        catch (SftpException e) {
            log.warn("mkdir: SftpException for {}", mUri, e);
        } catch (JSchException e) {
            log.warn("mkdir: JSchException for {}", mUri, e);
        } finally {
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
        }
        return false;
    }

    private InputStream wrapInputStream(final InputStream is, final Channel channel) {
        return new InputStream() {
            @Override
            public void close() throws IOException {
                is.close();
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }

            @Override
            public int read() throws IOException {
                return is.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return is.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return is.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return is.skip(n);
            }

            @Override
            public int available() throws IOException {
                return is.available();
            }

            @Override
            public void mark(int readlimit) {
                is.mark(readlimit);
            }

            @Override
            public void reset() throws IOException {
                is.reset();
            }

            @Override
            public boolean markSupported() {
                return is.markSupported();
            }
        };
    }

    @Override
    public InputStream getInputStream() throws FileNotFoundException, JSchException, SftpException {
        if (log.isDebugEnabled()) log.debug("getInputStream: {}", mUri.getPath());
        Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        InputStream is = ((ChannelSftp)channel).get(mUri.getPath());
        return wrapInputStream(is, channel);
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        if (log.isDebugEnabled()) log.debug("getInputStream: {} from {}", mUri.getPath(), from);
        final Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        InputStream is = ((ChannelSftp)channel).get(mUri.getPath(), null, from);
        return wrapInputStream(is, channel);
    }

    @Override
    public OutputStream getOutputStream() throws FileNotFoundException, JSchException, SftpException {
        if (log.isDebugEnabled()) log.debug("getOutputStream: {}", mUri.getPath());
        final Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
        final OutputStream sftpOS = ((ChannelSftp)channel).put(mUri.getPath());
        return new OutputStream() {
            @Override
            public void close() throws IOException {
                sftpOS.close();
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }

            @Override
            public void write(int b) throws IOException {
                sftpOS.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                sftpOS.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                sftpOS.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                sftpOS.flush();
            }
        };
    }

    @Override
    public Boolean delete() throws Exception {
        if (log.isDebugEnabled()) log.debug("delete: {}", mUri.getPath());
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rm(mUri.getPath());
        } catch (JSchException e) {
            log.warn("delete: JSchException for {}", mUri, e);
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
            if(e.getCause() instanceof java.net.UnknownHostException)
                throw new UnknownHostException();
            else
                throw new AuthenticationException();
        } catch (SftpException e) {
            log.warn("delete: SftpException for {}", mUri, e);
            throw new Exception("permission");
        } finally {
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
        }
        return null;
    }

    @Override
    public boolean rename(String newName){
        if (log.isDebugEnabled()) log.debug("rename: {} to {}", mUri.getPath(), newName);
        Channel channel = null;
        try {
             channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rename(mUri.getPath(), new File(new File(mUri.getPath()).getParentFile(), newName).getAbsolutePath());
            channel.disconnect();
            SFTPSession.getInstance().releaseSession(channel);
            return true;
        } catch (Exception e) {
            log.warn("rename: failed to rename {} to {}", mUri, newName, e);
        }finally {
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        if(!mUri.getScheme().equals(uri.getScheme())|| !mUri.getHost().equals(uri.getHost())||mUri.getPort()!=uri.getPort())
            return false;
        if (log.isDebugEnabled()) log.debug("move: {} to {}", mUri, uri);
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            ((ChannelSftp)channel).rename(mUri.getPath(),uri.getPath());
            channel.disconnect();
            SFTPSession.getInstance().releaseSession(channel);
            return true;
        } catch (Exception e) {
            log.warn("move: failed to move {} to {}", mUri, uri, e);
        }finally {
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
        }
        return false;
    }


    @Override
    public boolean exists() {
        if (log.isTraceEnabled()) log.trace("exists: checking {}", mUri.getPath());
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(mUri);
            SftpATTRS attrs =  ((ChannelSftp)channel).stat(mUri.getPath());
            channel.disconnect();
            SFTPSession.getInstance().releaseSession(channel);
            return attrs !=null;
        } catch (Exception e) {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
            if (DBG) Log.d(TAG, mUri + " not found");
            //generating an exception is the way to check if the file/dir exists thus silence the stacktrace
            //e.printStackTrace();
        }finally {
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
        }
        return false;
    }


}
