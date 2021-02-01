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
        public boolean accept(FTPFile f) {
            if (f == null) return false;
            final String filename = f.getName();
            if (filename.equals(".") || filename.equals("..")) {
                return false;
            }
            if (f.isSymbolicLink()) {
                // treat links as files
                // TODO: treat dir case
                return keepFile(filename);
            } else if (f.isFile()) {
                return keepFile(filename);
            } else if (f.isDirectory()) {
                return keepDirectory(filename);
            } else {
                log.debug("FTPFileFilter:accept neither file nor directory: " + filename);
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
                log.debug("FtpListingThread:run " + mUri);
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
                    if (f.isSymbolicLink()) { // it must be a symlink to file since dirs not selected
                        try {
                            FTPFile[] fToCheckA = null;
                            FTPFile fToCheck = null;
                            log.debug("FtpListingThread:run link detected " + f.getName() + " pointing to " + f.getLink() + ", checking mlistFile on " + mUri.getPath()+"/"+f.getLink());

                            // TODO MARC check listDirectories for directories or mlistDir

                            /*
                            // method 1: use new ftpClient --> FAILS EXCEPTION
                            log.debug("FtpListingThread:run link detected trying getFTPFile on " + Uri.parse(mUri+"/"+f.getLink()));
                            fToCheck = FTPFile2.getFTPFile(Uri.parse(mUri+"/"+f.getLink()));
                             */

                            // this one works with proftpd with mlst enabled in mod_facts
                            // method 2: use mlistFile on current client --> FAILS NULL
                            // /!\ WARNING: fToCheck.getName has full path since ftp root!
                            log.debug("FtpListingThread:run adding mlistFile on "+ mUri.getPath()+"/"+f.getLink());
                            if (isFtps) fToCheck = ftps.mlistFile(mUri.getPath()+"/"+f.getLink());
                            else fToCheck = ftp.mlistFile(mUri.getPath()+"/"+f.getLink());

                            /*
                            // method 3: use listFiles and take first element in array --> FAILS NULL
                            if (isFtps) fToCheckA = ftps.listFiles(mUri.getPath()+"/"+f.getLink());
                            else fToCheckA = ftp.listFiles(mUri.getPath()+"/"+f.getLink());
                            log.debug("FtpListingThread:run link detected listFiles returns length " + fToCheckA.length);
                            if (fToCheckA.length >0)
                                fToCheck = fToCheckA[0];
                             */

                            if (fToCheck == null) {
                                log.warn("FtpListingThread:run could not retrieve FTPFile " + f.getLink());
                            } else {
                                log.warn("FtpListingThread:run working on getLink " + f.getLink() + " whose getName is " + fToCheck.getName());
                                log.debug("FtpListingThread:run adding file " + fToCheck.getName() + " with uri "+ mUri.getScheme()+"://"+mUri.getHost()+":"+mUri.getPort()+fToCheck.getName());
                                FTPFile2 sf = new FTPFile2(fToCheck, Uri.parse(mUri.getScheme()+"://"+mUri.getHost()+":"+mUri.getPort()+fToCheck.getName()), f.getName());
                                if (sf.isDirectory()) {
                                    log.trace("FtpListingThread: add directory " + sf.getName());
                                    directories.add(sf);
                                } else {
                                    log.trace("FtpListingThread: add file " + sf.getName());
                                    files.add(sf);
                                }
                            }

                            /*
                            // nothing works: trust the link but it does not work afterwards (cannot access file)
                            FTPFile2 sf = new FTPFile2(f, Uri.withAppendedPath(mUri, f.getName()));
                            files.add(sf);
                             */


                        } catch (Exception e) {
                            log.warn("FtpListingThread:run caught exception following link on " + f.getName());
                        }
                    } else {
                        FTPFile2 sf = new FTPFile2(f, Uri.withAppendedPath(mUri, f.getName()), null);
                        if (sf.isDirectory()) {
                            log.trace("FtpListingThread: add directory " + sf.getName());
                            directories.add(sf);
                        } else {
                            log.trace("FtpListingThread: add file " + sf.getName());
                            files.add(sf);
                        }
                    }
                }
                if (isFtps) ftps.cwd("/");
                else ftp.cwd("/");

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
