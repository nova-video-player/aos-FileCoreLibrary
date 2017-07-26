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

package com.archos.filecorelibrary.contentstorage;

import android.content.Context;
import android.net.Uri;
import android.support.v4.provider.DocumentFile;
import android.util.Log;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


/**
 * This class handles the threading of the local storage file listing
 * @author vapillon
 *
 */
public class ContentProviderListingEngine extends ListingEngine {

    private final static String TAG = "ContentProviderListingEngine";

    final private Uri mUri;
    final private ListingThread mListingThread;
    private boolean mAbort = false;

    public ContentProviderListingEngine(Context context, Uri uri) {
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



    private final class ListingThread extends Thread {

        public void run(){
            try {

                Log.d(TAG, "listing files for " + mUri);
                DocumentFile.fromTreeUri(mContext, mUri);
                Log.d(TAG, "listing files for1 " + mUri);
                DocumentFile documentFile = DocumentUriBuilder.getDocumentFileForUri(mContext, mUri);
                Log.d(TAG, "listing files for2 " + mUri);
                // File not found error


                if (!documentFile.canRead()) {
                    postError(ErrorEnum.ERROR_NO_PERMISSION);
                    Log.d(TAG, "listing files for4 " + mUri);
                    return;
                }

                DocumentFile[] listFiles = documentFile.listFiles();

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

                final ArrayList<ContentFile2> directories = new ArrayList<>();
                final ArrayList<ContentFile2> files = new ArrayList<>();
                for (DocumentFile f : listFiles) {
                    if (f.isDirectory()&&keepDirectory(f.getName())) {

                       directories.add(new ContentFile2(f));
                    }
                    else if (f.isFile()&&keepFile(f.getName())) {
                        files.add(new ContentFile2(f));
                    }
                }

                // Put directories first, then files
                final Comparator<? super ContentFile2> comparator = new FileComparator().selectFileComparator(mSortOrder);
                Collections.sort(directories, comparator);
                Collections.sort(files, comparator);
                final ArrayList<ContentFile2> allFiles = new ArrayList<>(directories.size() + files.size());
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
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
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
