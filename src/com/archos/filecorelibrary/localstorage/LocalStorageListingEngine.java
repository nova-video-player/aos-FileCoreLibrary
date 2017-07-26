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

package com.archos.filecorelibrary.localstorage;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


/**
 * This class handles the threading of the local storage file listing
 * @author vapillon
 *
 */
public class LocalStorageListingEngine extends ListingEngine {

    private final static String TAG = "LocalStorageListingEngine";

    final private Uri mUri;
    final private ListingThread mListingThread;
    private boolean mAbort = false;

    public LocalStorageListingEngine(Context context, Uri uri) {
        super(context);

        mUri = uri;
        mListingThread = new ListingThread();
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

    private FileFilter mFileFilter = new FileFilter() {
        /**
         * @return true if the file must be kept
         */
        @Override
        public boolean accept(File f) {
            final String filename = f.getName();
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

    private final class ListingThread extends Thread {

        public void run(){
            try {

                Log.d(TAG, "listing files for " + mUri.getPath());
                File directory = new File(mUri.getPath());

                // File not found error
                if (!directory.exists()) {
                    postError(ErrorEnum.ERROR_FILE_NOT_FOUND);
                    return;
                }
                if (!directory.canRead()) {
                    postError(ErrorEnum.ERROR_NO_PERMISSION);
                    return;
                }

                File[] listFiles = directory.listFiles(mFileFilter);

                // Useful for test purpose
                //try { Thread.sleep(1000); } catch (InterruptedException e) {}

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

                // Check Error in reading the directory (java.io.File do not allow any details about the error...).
                if (listFiles == null) {
                    postError(ErrorEnum.ERROR_UNKNOWN);
                    return;
                }

                final ArrayList<JavaFile2> directories = new ArrayList<JavaFile2>();
                final ArrayList<JavaFile2> files = new ArrayList<JavaFile2>();
                for (File f : listFiles) {
                    if (f.isDirectory()) {
                        directories.add(new JavaFile2(f, JavaFile2.NUMBER_UNKNOWN, JavaFile2.NUMBER_UNKNOWN));
                    }
                    else if (f.isFile()) {
                        files.add(new JavaFile2(f));
                    }
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
                // Put directories first, then files
                final Comparator<? super JavaFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<JavaFile2> allFiles = new ArrayList<JavaFile2>(directories.size() + files.size());
                allFiles.addAll(directories);
                allFiles.addAll(files);
                // Send list to the the world
                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) {
                            mListener.onListingUpdate(allFiles);
                        }
                    }
                });


                mUiHandler.post(new Runnable() {
                    public void run() {
                        if (mListener != null) { // always report end even when aborted
                            mListener.onListingEnd();
                        }
                    }
                });

                for (final File f : listFiles) {
                    if (f.isDirectory()) {
                        // Count the files and folders inside this folder
                        int numerOfDirectories=0;
                        int numberOfFiles=0;
                        File[] insideFiles = f.listFiles(mFileFilter);
                        if(insideFiles!=null){
                            for (File insideFile : insideFiles) {
                                if (insideFile.isDirectory()) {
                                    numerOfDirectories++;
                                }
                                else if (insideFile.isFile()&&keepFile(insideFile.getName())) {
                                    numberOfFiles++;
                                }
                            }
                        }

                        final JavaFile2 javaFile2 = new JavaFile2(f, numberOfFiles, numerOfDirectories);
                        // Send update to the the world
                        if(!mAbort) {
                            mUiHandler.post(new Runnable() {
                                public void run() {
                                    if (mListener != null) {
                                        mListener.onListingFileInfoUpdate(javaFile2.getUri(), javaFile2);
                                    }
                                }
                            });
                        }

                    }

                }

            }
            finally {
               // noTimeOut(); // be sure there is no time out triggered after an error

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

    private void postError(final ErrorEnum error) {
        mUiHandler.post(new Runnable() {
            public void run() {
                if (!mAbort && mListener != null) { // do not report error if aborted
                    mListener.onListingFatalError(null, error);
                }
            }
        });
    }
}
