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

import com.archos.filecorelibrary.FileUtils;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.RawLister;
import com.archos.filecorelibrary.AuthenticationException;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;


/**
 * returns
 * @author alexandre
 *
 */
public class SFTPRawLister extends RawLister {

    private static final Logger log = LoggerFactory.getLogger(SFTPRawLister.class);

    public SFTPRawLister(Uri uri) {
        super(uri);
    }

    @Override
    public ArrayList<MetaFile2> getFileList() throws IOException, AuthenticationException, SftpException, JSchException {
        if (log.isDebugEnabled()) log.debug("getFileList: listing {}", mUri);
        Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);

        if(channel==null){
            
            return null;
        }

        ChannelSftp channelSftp = (ChannelSftp)channel;
        try {
            Vector<LsEntry> vec = channelSftp.ls(mUri.getPath().isEmpty() ? "/" : mUri.getPath());

            // Check Error in reading the directory.
            if (vec == null) {

                return null;
            }

            final ArrayList<MetaFile2> files = new ArrayList<MetaFile2>();
            for (LsEntry ls : vec) {
                if (ls.getFilename().equals(".") || ls.getFilename().equals(".."))
                    continue;
                if (log.isTraceEnabled()) log.trace("getFileList: entry {} isLink={} isDir={}", ls.getFilename(), ls.getAttrs().isLink(), ls.getAttrs().isDir());
                if (ls.getAttrs().isLink()) {
                    try {
                        String path = channelSftp.readlink(mUri.getPath() + "/" + ls.getFilename());
                        SftpATTRS stat = channelSftp.stat(path);
                        Uri newUri = FileUtils.buildChildUri(mUri, ls.getFilename());
                        SFTPFile2 sf = new SFTPFile2(stat, ls.getFilename(), newUri);
                        files.add(sf);
                    } catch (SftpException e) {
                        log.warn("getFileList: failed to resolve symlink {}", ls.getFilename(), e);
                    }
                } else {
                    SFTPFile2 sf = new SFTPFile2(ls.getAttrs(), ls.getFilename(), FileUtils.buildChildUri(mUri, ls.getFilename()));
                    files.add(sf);
                }
            }
            channel.disconnect();
            SFTPSession.getInstance().releaseSession(channel);
            return files;
        }catch (Exception e){
            log.warn("getFileList: failed to list {}", mUri, e);
            if(channel!=null&&channel.isConnected()) {
                channel.disconnect();
                SFTPSession.getInstance().releaseSession(channel);
            }
            throw e;
        }


    }
 
}
