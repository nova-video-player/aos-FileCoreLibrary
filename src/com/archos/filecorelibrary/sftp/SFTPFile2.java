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

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileEditor;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;


import java.io.File;
import java.net.UnknownHostException;

public class SFTPFile2 extends MetaFile2 {

    private static final long serialVersionUID = 2L;

    private final String mName;
    private final boolean mIsDirectory;
    private final boolean mIsFile;
    private final long mLastModified;
    private final boolean mCanRead;
    private final boolean mCanWrite;
    private final long mLength;
    private final String mUriString;
    
    /*
     * if this file is a symbolic link, filename isn't the same as file path
     * 
     */   
    public SFTPFile2(SftpATTRS stat, String filename, Uri uri) {
        if (filename == null){
            throw new IllegalArgumentException("filename cannot be null");
        }
        if (uri == null) {
            throw new IllegalArgumentException("uri cannot be null");
        }

        mUriString = uri.toString();
        mName = filename;
        mIsDirectory = stat.isDir();
        mIsFile = !stat.isDir();
        mLastModified = stat.getMTime();
        //TODO : permissions
        mCanRead = true;
        mCanWrite = true;
        mLength = stat.getSize();
    }

    @SuppressWarnings("unused")
    private SFTPFile2() {
        throw new IllegalArgumentException("Unauthorized to create a SFTPFile2 from nothing! Can only be created from a com.jcraft.jsch.SftpATTRS and an android.net.Uri");
    }

    @Override
    public String getName() {
        return mName;
    }

    @Override
    public boolean isDirectory() {
        return mIsDirectory;
    }

    @Override
    public boolean isFile() {
        return mIsFile;
    }

    @Override
    public long lastModified() {
        return mLastModified;
    }

    @Override
    public long length() {
        return mLength;
    }

    @Override
    public boolean canRead() {
        return mCanRead;
    }

    @Override
    public boolean canWrite() {
        return mCanWrite;
    }

    @Override
    public boolean isRemote() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof SFTPFile2) {
            return getUri().equals( ((SFTPFile2)other).getUri() );
        } else {
            return false;
        }
    }

    @Override
    public Uri getUri() {
        return Uri.parse(this.mUriString);
    }

    @Override
    public RawLister getRawListerInstance() {
        return new SFTPRawLister(getUri());
    }

    @Override
    public FileEditor getFileEditorInstance(Context ct) {
        return new SftpFileEditor(getUri());
    }

    /**
     * get metafile2 object from a uri (please use this only if absolutely necessary
     *
     */
    public static MetaFile2 fromUri(Uri uri) throws Exception {
        Channel channel = null;
        try {
            channel = SFTPSession.getInstance().getSFTPChannel(uri);
            SftpATTRS attrs = ((ChannelSftp)channel).stat(uri.getPath());
            channel.disconnect();
            return new SFTPFile2(attrs,uri.getLastPathSegment(), uri);
        } catch (JSchException e) {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
            if(e.getCause() instanceof java.net.UnknownHostException)
                throw new UnknownHostException();
            else
                throw new AuthenticationException();
        } catch (SftpException e) {
            if(channel!=null&&channel.isConnected())
                channel.disconnect();
            throw new Exception("permission");
        }
    }
}
