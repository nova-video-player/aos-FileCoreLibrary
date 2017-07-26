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

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.ChannelSftp.LsEntry;
import com.jcraft.jsch.ChannelSftp.LsEntrySelector;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpATTRS;
import com.jcraft.jsch.SftpException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Vector;

/**
 * This class handles the threading of the SFTP file listing
 * @author vapillon
 *
 */
public class SFtpListingEngine extends ListingEngine {

    private final static String TAG = "FtpListingEngine";

    final private Uri mUri;
    final private SFtpListingThread mListingThread;
    private boolean mAbort = false;

    public SFtpListingEngine(Context context, Uri uri) {
        super(context);
        mUri = uri;
        mListingThread = new SFtpListingThread();
    }

    @Override
    public void start() {
        // Tell ASAP the listener that we are starting discovery
        mUiHandler.post(new Runnable() {
            public void run() {
                if (mListener != null) {
                    mListener.onListingStart();
                }
            }
        });

        mListingThread.start();

        preLaunchTimeOut();
    }

    @Override
    public void abort() {
        mAbort = true;
    }

    private Vector<LsEntry> listEntries(final ChannelSftp channelSftp, final String path) throws SftpException {
        final Vector<LsEntry> vector = new Vector<LsEntry>();

        LsEntrySelector selector = new LsEntrySelector() {
            public int select(LsEntry entry)  {
                final String filename = entry.getFilename();
                if (filename.equals(".") || filename.equals("..")) {
                    return CONTINUE;
                }
                if (entry.getAttrs().isLink()) {
                    vector.addElement(entry);
                }
                else if (entry.getAttrs().isDir()) {
                    if (keepDirectory(filename)) {
                        vector.addElement(entry);
                    }
                }
                else {
                     if (keepFile(filename)) {
                        vector.addElement(entry);
                    }
                }
                return CONTINUE;
            }
        };

        channelSftp.ls(path, selector);
        return vector;
    }

    private final class SFtpListingThread extends Thread {
        public void run(){     
            try {
                Channel channel = SFTPSession.getInstance().getSFTPChannel(mUri);
                if(channel==null&&!mAbort ){
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_UNKNOWN);
                            }
                        }
                    });
                    return;
                }
                if (timeOutHasOccurred() || mAbort) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) { // always report end even when aborted
                                mListener.onListingEnd();
                            }
                        }
                    });
                    return;
                }
                ChannelSftp channelSftp = (ChannelSftp)channel;
                Vector<LsEntry> vector = listEntries(channelSftp, mUri.getPath().isEmpty() ? "/" : mUri.getPath());
                // Check if timeout or abort occurred
                if (timeOutHasOccurred() || mAbort) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) { // always report end even when aborted
                                mListener.onListingEnd();
                            }
                        }
                    });
                    return;
                }

                // Avoid to have time-out triggered while doing the "post-processing" of the list
                noTimeOut();

                // Check Error in reading the directory.
                if (vector == null) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_UNKNOWN);
                            }
                        }
                    });
                    return;
                }

                final ArrayList<SFTPFile2> directories = new ArrayList<SFTPFile2>();
                final ArrayList<SFTPFile2> files = new ArrayList<SFTPFile2>();
                for (LsEntry ls : vector) {
                    if (ls.getAttrs().isLink()) {
                        try {
                            String filename = ls.getFilename();
                            String path = channelSftp.readlink(mUri.getPath()+"/"+ls.getFilename());
                            SftpATTRS stat = channelSftp.stat(path);
                            Uri newUri = Uri.withAppendedPath(mUri, ls.getFilename());
                            SFTPFile2 sf = new SFTPFile2 ( stat, ls.getFilename(),newUri);
                            if(stat.isDir()&&keepDirectory(filename)){
                                directories.add(sf);
                            }
                            else {
                                if (keepFile(filename)) {
                                    files.add(sf);
                                }
                            }
                        } catch (SftpException e) {
                        }
                    }
                    else {
                        SFTPFile2 sf = new SFTPFile2(ls.getAttrs(), ls.getFilename(), Uri.withAppendedPath(mUri, ls.getFilename()));
                        if (sf.isDirectory()) {
                            directories.add(sf);
                        }
                        else {
                            files.add(sf);
                        }
                    }
                }

                // Put directories first, then files
                final Comparator<? super SFTPFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<SFTPFile2> allFiles = new ArrayList<SFTPFile2>(directories.size() + files.size());
                allFiles.addAll(directories);
                allFiles.addAll(files);

                // Check if timeout or abort occurred
                if (timeOutHasOccurred() || mAbort) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) { // always report end even when aborted
                                mListener.onListingEnd();
                            }
                        }
                    });
                    return;
                }

                // Send list to the the world
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) {
                            mListener.onListingUpdate(allFiles);
                        }
                    }
                });
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) {
                            mListener.onListingEnd();
                        }
                    }
                });
            } catch (final SftpException e) {
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) {
                            mListener.onListingFatalError(e, ErrorEnum.ERROR_NO_PERMISSION);
                        }
                    }
                });
            }  
            catch (final JSchException e1) {
                if(e1.getCause() instanceof java.net.UnknownHostException)
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) {
                                mListener.onListingFatalError(e1, ErrorEnum.ERROR_HOST_NOT_FOUND);
                            }
                        }
                    });
                else
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) {
                                mListener.onCredentialRequired(e1);
                            }
                        }
                    });
            } 
        }
    }
}
