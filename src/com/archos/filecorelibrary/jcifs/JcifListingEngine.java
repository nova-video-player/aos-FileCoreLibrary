// Copyright 2017 Archos SA
// Copyright 2019 Courville Software
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

package com.archos.filecorelibrary.jcifs;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileFilter;

import static com.archos.filecorelibrary.jcifs.JcifsUtils.getSmbFile;

/**
 * This class handles the threading of the Jcifs (Samba) file listing
 * @author vapillon
 *
 */
public class JcifListingEngine extends ListingEngine {

    private static final Logger log = LoggerFactory.getLogger(JcifListingEngine.class);

    final private Uri mUri;
    final private JcifListingThread mListingThread;
    private boolean mAbort = false;

    public JcifListingEngine(Context context, Uri uri) {
        super(context);
        if(!uri.toString().endsWith("/"))// directory must end with "/"
            mUri = Uri.withAppendedPath(uri,"");
        else mUri = uri;
        mListingThread = new JcifListingThread();
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

    private SmbFileFilter mFileFilter = new SmbFileFilter() {
        /**
         * @return true if the file must be kept
         */
        @Override
        public boolean accept(SmbFile f) {
            final String filename = f.getName();
            try {
                if (f.isFile()) { // IMPORTANT: call the _noquery version to avoid network access
                    return keepFile(filename);
                }
                else if (f.isDirectory()) { // IMPORTANT: call the _noquery version to avoid network access
                    return keepDirectory(filename);
                }
                else {
                    log.debug("SmbFileFilter: neither file nor directory: "+filename);
                    return false;
                }
            } catch (SmbException e) {
                if (log.isTraceEnabled()) log.error("SmbFileFilter: caught SmbException: ", e);
                else log.error("SmbFileFilter: caught SmbException");
            }
            return false;
        }
    };

    private final class JcifListingThread extends Thread {

        public void run(){
            try {
                log.debug("JcifListingThread: listFiles for: " + mUri.toString());
                NovaSmbFile nSmbFile = getSmbFile(mUri);
                SmbFile[] listFiles = nSmbFile.smbFile.listFiles(mFileFilter);
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
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_UNKNOWN);
                            }
                        }
                    });
                    return;
                }

                // sorting entries
                final ArrayList<JcifsFile2> directories = new ArrayList<>();
                final ArrayList<JcifsFile2> files = new ArrayList<>();
                for (SmbFile f : listFiles) {
                    if (f.isFile()) { // IMPORTANT: call the _noquery version to avoid network access
                        log.trace("JcifListingThread: adding file " + f.getPath());
                        files.add(new JcifsFile2(f, nSmbFile.shareName, nSmbFile.shareIP));
                    }
                    else if (f.isDirectory()) { // IMPORTANT: call the _noquery version to avoid network access
                        log.trace("JcifListingThread: adding directory " + f.getPath());
                        directories.add(new JcifsFile2(f, nSmbFile.shareName, nSmbFile.shareIP));
                    }
                }

                final Comparator<? super JcifsFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                final ArrayList<JcifsFile2> allFiles = new ArrayList<>(directories.size() + files.size());

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

                // Check if abort occurred (Well, checking here again in case the sorting is very long, for some reason...)
                if (mAbort) {
                    log.debug("JcifListingThread: abort");
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
            catch (final SmbAuthException e) {
                if (log.isTraceEnabled()) log.error("JcifListingThread: SmbAuthException for " + mUri.toString(), e);
                else log.warn("JcifListingThread: SmbAuthException for " + mUri.toString());
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onCredentialRequired(e);
                        }
                    }
                });
            }
            catch (final SmbException e) {
                ErrorEnum error = ErrorEnum.ERROR_UNKNOWN;
                if (e.getCause() instanceof UnknownHostException) {
                    error = ErrorEnum.ERROR_UNKNOWN_HOST;
                }
                final ErrorEnum fError = error;
                if (log.isTraceEnabled()) log.error("JcifListingThread: SmbException (" + getErrorStringResId(error) + ") for " + mUri.toString(), e);
                else log.error("JcifListingThread: SmbException (" + getErrorStringResId(error) + ") for " + mUri.toString());
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onListingFatalError(e, fError);
                        }
                    }
                });
            }
            catch (final MalformedURLException e) {
                if (log.isTraceEnabled()) log.error("JcifListingThread: MalformedURLException for " + mUri.toString(), e);
                else log.error("JcifListingThread: MalformedURLException for " + mUri.toString());
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onListingFatalError(e, ErrorEnum.ERROR_UNKNOWN);
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
