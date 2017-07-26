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

package com.archos.filecorelibrary.zip;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.archos.filecorelibrary.FileComparator;
import com.archos.filecorelibrary.ListingEngine;
import com.archos.filecorelibrary.localstorage.JavaFile2;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


/**
 * This class handles the threading of the local storage file listing
 * @author vapillon
 *
 */
public class ZipListingEngine extends ListingEngine {

    private final static String TAG = "LocalStorageListingEngine";

    final private Uri mUri;
    final private ListingThread mListingThread;
    private boolean mAbort = false;

    public ZipListingEngine(Context context, Uri uri) {
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
    private void listZip(){


    }
    private final class ListingThread extends Thread {

        public void run(){
            try {

                Log.d(TAG, "listing files for " + mUri.getPath());

                /*
            for each path segment, check if file (in case we have a folder with .zip in the name)

         */
                String toTest = ZipUtils.getZipPathFromUri(mUri);
                String path = mUri.getPath();
                String remains ="";
                if(path.length()>toTest.length()+1)
                    remains = path.substring(toTest.length()+1);//remove first "/"
                try {
                    ZipFile zf = new ZipFile(toTest);
                    ArrayList <ZipEntry>entries = (ArrayList<ZipEntry>) Collections.list(zf.entries());
                    if (entries == null) {
                        postError(ErrorEnum.ERROR_UNKNOWN);
                        return;
                    }
                    ArrayList<ZipFile2> files = new ArrayList<>();
                    ArrayList<ZipFile2> directories = new ArrayList<>();
                    for(ZipEntry entry : entries){
                        if(entry.getName().startsWith(remains)||remains.equals("")){
                            String name = entry.getName().substring(remains.length());
                            boolean rightLevel = !name.equalsIgnoreCase("") && (name.indexOf('/') == name.length()-1 || name.indexOf('/') == -1);
                            if(rightLevel){

                                ZipFile2 zf2 = new ZipFile2(toTest, entry);
                                if(zf2.isFile())
                                    files.add(zf2);
                                else
                                    directories.add(zf2);
                            }
                        }
                    }
                    final ArrayList<ZipFile2> allFiles = new ArrayList<>(directories.size() + files.size());
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
                } catch (IOException e) {
                    postError(ErrorEnum.ERROR_UNKNOWN);
                }

                // Useful for test purpose
                //try { Thread.sleep(5000); } catch (InterruptedException e) {}

                // Check if timeout or abort occurred
                if (timeOutHasOccurred() || mAbort) {
                    mUiHandler.post(new Runnable() {
                        public void run() {
                            if (mListener != null) { // always report end even when aborted²
                                mListener.onListingEnd();
                            }
                        }
                    });
                    return;
                }
                // Check Error in reading the directory (java.io.File do not allow any details about the error...).


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
