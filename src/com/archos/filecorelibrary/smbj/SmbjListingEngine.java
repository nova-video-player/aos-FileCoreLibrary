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

package com.archos.filecorelibrary.smbj;

import static com.archos.filecorelibrary.FileUtils.getFilePath;
import static com.archos.filecorelibrary.FileUtils.getShareName;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.protocol.commons.EnumWithValue;
import com.thegrizzlylabs.sardineandroid.DavResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * This class handles the threading of the Smbj file listing
 */
public class SmbjListingEngine extends ListingEngine {

    private static final Logger log = LoggerFactory.getLogger(SmbjListingEngine.class);

    final private Uri mUri;
    final private SmbjListingThread mListingThread;
    private boolean mAbort = false;

    public SmbjListingEngine(Context context, Uri uri) {
        super(context);
        if(!uri.toString().endsWith("/"))// directory must end with "/"
            mUri = Uri.withAppendedPath(uri,"");
        else mUri = uri;
        mListingThread = new SmbjListingThread();
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

    // TODO MARC move elswhere
    public static boolean isDirectory(FileIdBothDirectoryInformation fileEntry) {
        return EnumWithValue.EnumUtils.isSet(fileEntry.getFileAttributes(), FileAttributes.FILE_ATTRIBUTE_DIRECTORY);
    }

    private final class SmbjListingThread extends Thread {

        public void run(){
            try {
                log.debug("SmbjListingThread: listFiles for: " + mUri.toString());

                var diskShare = SmbjUtils.peekInstance().getSmbShare(mUri);
                String filePath = getFilePath(mUri);

                var acceptedDiskShareLst = new ArrayList<FileIdBothDirectoryInformation>();
                List<FileIdBothDirectoryInformation> diskShareLst = diskShare.list(filePath);

                final ArrayList<SmbjFile2> directories = new ArrayList<>();
                final ArrayList<SmbjFile2> files = new ArrayList<>();

                // TODO MARC remove comment and 0?
                // First answer is ourselves, ignore it
                //davResources.remove(0);
                final String shareName = getShareName(mUri);
                for (FileIdBothDirectoryInformation fileOrDir : diskShareLst) {
                    // TODO MARC it does not have the full uri...
                    final String filename = fileOrDir.getFileName();
                    final String fullFilename = "/" + shareName + "/" + filename;
                    if (isDirectory(fileOrDir)) {
                        if (keepDirectory(filename)) {
                            log.trace("SmbjListingThread: adding directory " + fullFilename);
                            directories.add(new SmbjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(fullFilename).build()));
                        }
                    } else { // this is a file
                        if (keepFile(filename)) {
                            log.trace("SmbjListingThread: adding file " + fullFilename);
                            files.add(new SmbjFile2(fileOrDir, mUri.buildUpon().appendEncodedPath(fullFilename).build()));
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
                final Comparator<? super SmbjFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<SmbjFile2> allFiles = new ArrayList<>(directories.size() + files.size());
                allFiles.addAll(directories);
                allFiles.addAll(files);

                // Check if abort occurred (Well, checking here again in case the sorting is very long, for some reason...)
                if (mAbort) {
                    log.debug("SmbjListingThread: abort");
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
            }
            // TODO MARC
            /*
            catch (final AuthenticationException e) {
                if (log.isTraceEnabled()) log.error("SmbjListingThread: SmbAuthException for " + mUri.toString(), e);
                else log.warn("SmbjListingThread: SmbAuthException for " + mUri.toString());
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onCredentialRequired(e);
                        }
                    }
                });
            } */
            catch (final IOException e) {
                ErrorEnum error = ErrorEnum.ERROR_UNKNOWN;
                if (e.getCause() instanceof UnknownHostException) {
                    error = ErrorEnum.ERROR_UNKNOWN_HOST;
                }
                final ErrorEnum fError = error;
                if (log.isTraceEnabled()) log.error("SmbjListingThread: IOException (" + getErrorStringResId(error) + ") for " + mUri.toString(), e);
                else log.error("SmbjListingThread: IOException (" + getErrorStringResId(error) + ") for " + mUri.toString());
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onListingFatalError(e, fError);
                        }
                    }
                });
            }
            finally {
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