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

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFileFilter;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * This class handles the threading of the FTP file listing
 * @author vapillon
 *
 */
public class FtpListingEngine extends ListingEngine {

    private final static String TAG = "FtpListingEngine";

    final private Uri mUri;
    final private FtpListingThread mListingThread;
    private boolean mAbort = false;

    public FtpListingEngine(Context context, Uri uri) {
        super(context);
        mUri = uri;
        mListingThread = new FtpListingThread();
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

    private void postFatalError(final Exception e) {
        mUiHandler.post(new Runnable() {
            public void run() {
                if (!mAbort && mListener != null) { // do not report error if aborted
                    ErrorEnum error;
                    if(e instanceof java.net.UnknownHostException)
                        error = ErrorEnum.ERROR_HOST_NOT_FOUND;
                    else
                        error = ErrorEnum.ERROR_UNKNOWN;
                    mListener.onListingFatalError(e,error);
                }
            }
        });
    }

    private FTPFileFilter mFileFilter = new FTPFileFilter() {
        /**
         * @return true if the file must be kept
         */
        @Override
        public boolean accept(org.apache.commons.net.ftp.FTPFile f) {
            final String filename = f.getName();
            if (filename.equals(".") || filename.equals("..")) {
                return false;
            }
            if (f.isFile()) {
                return keepFile(filename);
            }
            else if (f.isDirectory()) {
                return keepDirectory(filename);
            }
            else {
                Log.d(TAG, "neither file nor directory: "+filename);
                return false;
            }
        }
    };

    private final class FtpListingThread extends Thread {

        public void run(){
            try {
                FTPClient ftp;

                if(mUri.getScheme().equals("ftps"))
                        ftp= Session.getInstance().getFTPSClient(mUri);
                    else
                        ftp= Session.getInstance().getFTPClient(mUri);
                ftp.cwd(mUri.getPath());
                org.apache.commons.net.ftp.FTPFile[] listFiles = ftp.listFiles(null, mFileFilter);  // list files(path) doesn't work when white spaces in names

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
                if (listFiles == null) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (!mAbort && mListener != null) { // do not report error if aborted
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_FILE_NOT_FOUND);
                            }
                        }
                    });
                    return;
                }

                final ArrayList<FTPFile2> directories = new ArrayList<FTPFile2>();
                final ArrayList<FTPFile2> files = new ArrayList<FTPFile2>();
                for(org.apache.commons.net.ftp.FTPFile f : listFiles){
                    FTPFile2 sf = new FTPFile2(f , Uri.withAppendedPath(mUri, f.getName()));
                    if (sf.isDirectory()) {
                        directories.add(sf);
                    } else {
                        files.add(sf);
                    }
                }
                ftp.cwd("/");

                // Put directories first, then files
                final Comparator<? super FTPFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<FTPFile2> allFiles = new ArrayList<FTPFile2>(directories.size() + files.size());
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

                // Check if abort occurred (Well, checking here again in case the sorting is very long, for some reason...)
                if (mAbort) {
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
            catch (UnknownHostException e) {
                Log.e(TAG, "FtpListingThread",e);
                postFatalError(e);
            }
            catch (SocketException e) {
                Log.e(TAG, "FtpListingThread",e);
                postFatalError(e);
            }
            catch (IOException e) {
                Log.e(TAG, "FtpListingThread",e);
                postFatalError(e);
            }
            catch (final AuthenticationException e) {
                Log.e(TAG, "FtpListingThread",e);
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not ask credentials if aborted
                            mListener.onCredentialRequired(e);
                        }
                    }
                });
            }
            finally {
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) { // always report end even when aborted or ended
                            mListener.onListingEnd();
                        }
                    }
                });
            }
        }
    }
}
