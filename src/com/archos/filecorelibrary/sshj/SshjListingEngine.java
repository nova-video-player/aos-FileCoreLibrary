// Copyright 2023 Courville Software
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

package com.archos.filecorelibrary.sshj;

import static com.archos.filecorelibrary.FileUtils.caughtException;
import static com.archos.filecorelibrary.FileUtils.encodeUri;
import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;

import net.schmizz.sshj.sftp.RemoteResourceInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class handles the threading of the Sshj file listing
 */
public class SshjListingEngine extends ListingEngine {

    private static final Logger log = LoggerFactory.getLogger(SshjListingEngine.class);

    final private Uri mUri;
    final private SshjListingThread mListingThread;
    private boolean mAbort = false;

    public SshjListingEngine(Context context, Uri uri) {
        super(context);
        if(!uri.toString().endsWith("/"))// directory must end with "/"
            mUri = Uri.withAppendedPath(uri,"");
        else mUri = uri;
        mListingThread = new SshjListingThread();
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

    private final class SshjListingThread extends Thread {

        public void run(){
            try {
                log.debug("SshjListingThread: listFiles for: " + mUri.toString());

                var sftpClient = SshjUtils.peekInstance().getSFTPClient(mUri);
                String filePath = encodeUri(mUri).getPath();

                var acceptedDiskShareLst = new ArrayList<FileIdBothDirectoryInformation>();
                List<RemoteResourceInfo> lst = sftpClient.ls(filePath);

                final ArrayList<SshjFile2> directories = new ArrayList<>();
                final ArrayList<SshjFile2> files = new ArrayList<>();

                final String shareName = getShareName(mUri);
                for (RemoteResourceInfo fileOrDir : lst) {
                    final String filename = fileOrDir.getName();
                    if (fileOrDir.isDirectory()) {
                        if (keepDirectory(filename)) {
                            log.trace("SshjListingThread: adding directory " + filename);
                            directories.add(new SshjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(filename).build()));
                        }
                    } else { // this is a file
                        if (keepFile(filename)) {
                            log.trace("SshjListingThread: adding file " + filename);
                            files.add(new SshjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(filename).build()));
                        }
                    }
                }

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
                if (acceptedDiskShareLst == null) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_UNKNOWN);
                            }
                        }
                    });
                    return;
                }

                // Put directories first, then files
                final Comparator<? super SshjFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<SshjFile2> allFiles = new ArrayList<>(directories.size() + files.size());
                allFiles.addAll(directories);
                allFiles.addAll(files);

                // Check if abort occurred (Well, checking here again in case the sorting is very long, for some reason...)
                if (mAbort) {
                    log.debug("SshjListingThread: abort");
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
                // TODO MARC NOK
            } catch (IOException ioe) { // auth exception most probably
                if (ioe.getMessage().contains("Permission denied")) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onCredentialRequired(ioe);
                            }
                        }
                    });
                } else {
                    ErrorEnum error = ErrorEnum.ERROR_UNKNOWN;
                    if (ioe.getCause() instanceof UnknownHostException) {
                        error = ErrorEnum.ERROR_UNKNOWN_HOST;
                    }
                    final ErrorEnum fError = error;
                    caughtException(ioe, "SshjListingEngine:SshjListingThread", "IOException (" + getErrorStringResId(error) + ") for " + mUri);
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onListingFatalError(ioe, fError);
                            }
                        }
                    });
                }
            } finally {
                noTimeOut(); // be sure there is no time out triggered after an error

                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) { // always report end even when aborted
                            mListener.onListingEnd();
                        }
                    }
                });
            }
        }
    }
}
