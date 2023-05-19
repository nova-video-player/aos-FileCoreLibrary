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

package com.archos.filecorelibrary.webdav;

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.thegrizzlylabs.sardineandroid.DavResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

/**
 * This class handles the threading of the Webdav file listing
 */
public class WebdavListingEngine extends ListingEngine {

    private static final Logger log = LoggerFactory.getLogger(WebdavListingEngine.class);

    final private Uri mUri;
    final private WebdavListingThread mListingThread;
    private boolean mAbort = false;

    public WebdavListingEngine(Context context, Uri uri) {
        super(context);
        if(!uri.toString().endsWith("/"))// directory must end with "/"
            mUri = Uri.withAppendedPath(uri,"");
        else mUri = uri;
        mListingThread = new WebdavListingThread();
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

    private final class WebdavListingThread extends Thread {

        public void run(){
            try {
                log.debug("WebdavListingThread: listFiles for: " + mUri.toString());

                var sardine = WebdavUtils.peekInstance().getSardine(mUri);
                var httpUri = WebdavFile2.uriToHttp(mUri);

                var acceptedDavResources = new ArrayList<DavResource>();
                var davResources = sardine.list(httpUri.toString());

                final ArrayList<WebdavFile2> directories = new ArrayList<>();
                final ArrayList<WebdavFile2> files = new ArrayList<>();

                // First answer is ourselves, ignore it
                davResources.remove(0);
                for (var davResource : davResources) {
                    final String filename = davResource.getName();
                    if (davResource.isDirectory()) {
                        if (keepDirectory(filename)) {
                            log.trace("WebdavListingThread: adding directory " + davResource.getPath());
                            directories.add(new WebdavFile2(davResource, mUri.buildUpon().appendEncodedPath(davResource.getName()).build()));
                        }
                    } else { // this is a file
                        if (keepFile(filename)) {
                            log.trace("WebdavListingThread: adding file " + davResource.getPath());
                            //listFiles.add(new WebdavFile2(davResource, mUri.buildUpon().appendEncodedPath(davResource.getName()).build()));
                            files.add(new WebdavFile2(davResource, mUri.buildUpon().appendEncodedPath(davResource.getName()).build()));
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
                if (acceptedDavResources == null) {
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
                final Comparator<? super WebdavFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                final ArrayList<WebdavFile2> allFiles = new ArrayList<>(directories.size() + files.size());

                if (mSortOrder.ordinal() == 0 || mSortOrder.ordinal() == 1) { // sort by URI
                    // by size: directories displayed first, then files
                    Collections.sort(directories, comparator);
                    Collections.sort(files, comparator);
                    allFiles.addAll(directories);
                    allFiles.addAll(files);
                } else if (mSortOrder.ordinal() == 2 || mSortOrder.ordinal() == 3) { // sort by NAME
                    // by name: both files and directories are sorted
                    allFiles.addAll(directories);
                    allFiles.addAll(files);
                    Collections.sort(allFiles, comparator);
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
                    log.debug("WebdavListingThread: abort");
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
                if (log.isTraceEnabled()) log.error("WebdavListingThread: SmbAuthException for " + mUri.toString(), e);
                else log.warn("WebdavListingThread: SmbAuthException for " + mUri.toString());
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
                if (log.isTraceEnabled()) log.error("WebdavListingThread: IOException (" + getErrorStringResId(error) + ") for " + mUri.toString(), e);
                else log.error("WebdavListingThread: IOException (" + getErrorStringResId(error) + ") for " + mUri.toString());
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
