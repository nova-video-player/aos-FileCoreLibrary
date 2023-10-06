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

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPFileFilter;
import org.apache.commons.net.ftp.FTPSClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.archos.filecorelibrary.AuthenticationException;

/**
 * This class handles the threading of the FTP file listing
 * @author vapillon
 *
 */
public class FtpListingEngine extends ListingEngine {

    private static final Logger log = LoggerFactory.getLogger(FtpListingEngine.class);
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
        if (f != null) {
            final String filename = f.getName();
            if (filename.equals(".") || filename.equals("..")) {
                return false;
            }
            if (f.isFile()) {
                return keepFile(filename);
            } else if (f.isDirectory()) {
                return keepDirectory(filename);
            } else {
                log.debug("neither file nor directory: " + filename);
                return false;
            }
        } else {
            return false;
        }
        }
    };

    private final class FtpListingThread extends Thread {

        public void run(){
            Boolean isFtps = false;
            FTPSClient ftps = null;
            FTPClient ftp = null;
            try {
                log.debug("FtpListingThread:run");
                FTPFile[] listFiles;
                if (mUri.getScheme().equals("ftps")) {
                    ftps = Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
                    ftps.cwd(mUri.getPath());
                    listFiles = ftps.listFiles(null, mFileFilter);  // list files(path) doesn't work when white spaces in names
                    isFtps = true;
                } else {
                    ftp = Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
                    ftp.cwd(mUri.getPath());
                    listFiles = ftp.listFiles(null, mFileFilter);  // list files(path) doesn't work when white spaces in names
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
                for (FTPFile f : listFiles){
                    FTPFile2 sf = new FTPFile2(f, Uri.withAppendedPath(mUri, f.getName()));
                    if (sf.isDirectory()) {
                        log.trace("FtpListingThread: add directory " + sf.getName());
                        directories.add(sf);
                    } else {
                        log.trace("FtpListingThread: add file " + sf.getName());
                        files.add(sf);
                    }
                }
                if (isFtps) ftps.cwd("/");
                else ftp.cwd("/");

                // sorting entries
                final Comparator<? super FTPFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                final ArrayList<FTPFile2> allFiles = new ArrayList<>(directories.size() + files.size());

                if (mSortOrder.ordinal() == 0 || mSortOrder.ordinal() == 1) { // sort by URI
                    // by size: directories displayed first, then files
                    Collections.sort(directories, comparator);
                    Collections.sort(files, comparator);
                    allFiles.addAll(directories);
                    allFiles.addAll(files);
                } else if (mSortOrder.ordinal() == 2 || mSortOrder.ordinal() == 3) { // sort by NAME
                    // keep directories first then files (legacy behavior)
                    Collections.sort(directories, comparator);
                    Collections.sort(files, comparator);
                    allFiles.addAll(directories);
                    allFiles.addAll(files);
                    // by name: both files and directories are sorted: better when files are mixed with directories in download folder but changes habits
                    //allFiles.addAll(directories);
                    //allFiles.addAll(files);
                    //Collections.sort(allFiles, comparator);
                } else if (mSortOrder.ordinal() == 4 || mSortOrder.ordinal() == 5) { // sort by SIZE
                    // by size: files displayed first, then directories
                    Collections.sort(files, comparator);
                    allFiles.addAll(files);
                    Collections.sort(directories, comparator);
                    allFiles.addAll(directories);
                } else if (mSortOrder.ordinal() == 6 || mSortOrder.ordinal() == 7) { // sort by DATE
                    // by date: both files and directories are sorted
                    allFiles.addAll(directories);
                    allFiles.addAll(files);
                    Collections.sort(allFiles, comparator);
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
            } catch (UnknownHostException e) {
                log.error("FtpListingThread",e);
                postFatalError(e);
            } catch (SocketException e) {
                log.error("FtpListingThread",e);
                postFatalError(e);
            } catch (IOException e) {
                log.error("FtpListingThread",e);
                postFatalError(e);
            } catch (final AuthenticationException e) {
                log.error("FtpListingThread",e);
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not ask credentials if aborted
                            mListener.onCredentialRequired(e);
                        }
                    }
                });
            } finally {
                if (isFtps) Session.closeNewFTPSClient(ftps);
                else Session.closeNewFTPClient(ftp);
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
