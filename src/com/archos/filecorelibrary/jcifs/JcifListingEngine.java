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

package com.archos.filecorelibrary.jcifs;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import java.net.MalformedURLException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import jcifs2.smb.NtlmPasswordAuthentication;
import jcifs2.smb.SmbAuthException;
import jcifs2.smb.SmbException;
import jcifs2.smb.SmbFile;
import jcifs2.smb.SmbFileFilter;


/**
 * This class handles the threading of the Jcifs (Samba) file listing
 * @author vapillon
 *
 */
public class JcifListingEngine extends ListingEngine {

    private final static String TAG = "JcifListingEngine";

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
            if (f.isFile_noquery()) { // IMPORTANT: call the _noquery version to avoid network access
                return keepFile(filename);
            }
            else if (f.isDirectory_noquery()) { // IMPORTANT: call the _noquery version to avoid network access
                return keepDirectory(filename);
            }
            else {
                Log.d(TAG, "neither file nor directory: "+filename);
                return false;
            }
        }
    };

    private final class JcifListingThread extends Thread {

        public void run(){
            try {
                Log.d(TAG, "listFiles for:"+mUri.toString());
                NetworkCredentialsDatabase.Credential cred = NetworkCredentialsDatabase.getInstance().getCredential(mUri.toString());
                SmbFile[] listFiles;
                if (cred != null) {
                    NtlmPasswordAuthentication auth = new NtlmPasswordAuthentication("", cred.getUsername(), cred.getPassword());
                    listFiles = new SmbFile(mUri.toString(), auth).listFiles(mFileFilter);
                }
                else {
                    listFiles = new SmbFile(mUri.toString()).listFiles(mFileFilter);
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
                                mListener.onListingFatalError(null, ErrorEnum.ERROR_UNKNOWN);
                            }
                        }
                    });
                    return;
                }

                final ArrayList<JcifsFile2> directories = new ArrayList<>();
                final ArrayList<JcifsFile2> files = new ArrayList<>();
                for (SmbFile f : listFiles) {
                    if (f.isFile_noquery()) { // IMPORTANT: call the _noquery version to avoid network access
                        files.add(new JcifsFile2(f));
                    }
                    else if (f.isDirectory_noquery()) { // IMPORTANT: call the _noquery version to avoid network access
                        directories.add(new JcifsFile2(f));
                    }
                }

                // Put directories first, then files
                final Comparator<? super JcifsFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<JcifsFile2> allFiles = new ArrayList<>(directories.size() + files.size());
                allFiles.addAll(directories);
                allFiles.addAll(files);

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
            catch (final SmbAuthException e) {
                Log.d(TAG, "JcifListingThread: SmbAuthException", e);
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
                if (e.getRootCause() instanceof UnknownHostException) {
                    error = ErrorEnum.ERROR_UNKNOWN_HOST;
                }
                final ErrorEnum fError = error;
                Log.e(TAG, "JcifListingThread: SmbException", e);
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (!mAbort && mListener != null) { // do not report error if aborted
                            mListener.onListingFatalError(e, fError);
                        }
                    }
                });
            }
            catch (final MalformedURLException e) {
                Log.e(TAG, "JcifListingThread: MalformedURLException", e);
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
