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
import android.os.Handler;
import android.os.Looper;

import com.archos.filecorelibrary.CopyCutEngine;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.OperationEngineListener;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by alexandre on 20/07/15.
 */
public class ZipExtractionEngine {
    private final Handler mUiHandler;
    private final OperationEngineListener mListener;
    private final Context mContext;
    private ExtractThread mExtractThread;
    private boolean mHasToStop;
    private CopyCutEngine mCopyEngine;


    public void stop() {
        mHasToStop = true;
        if(mCopyEngine!=null)
            mCopyEngine.stop();
    }


    public ZipExtractionEngine(OperationEngineListener listener, Context context){
        mListener = listener;
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());

    }
    public void extract(List<MetaFile2> toCompress, Uri target){
        if(mExtractThread !=null&& mExtractThread.isAlive())
            return;
        mHasToStop = false;
        mExtractThread = new ExtractThread();
        mExtractThread.set(toCompress, target);
        mExtractThread.start();
    }
    public void abort(){
        mHasToStop = true;
    }

    private class ExtractThread extends  Thread{
        private List<MetaFile2> mToExtract;
        private Uri mTarget;



        private List<MetaFile2> listZipFile(MetaFile2 file) throws Exception {

            ZipRawLister rawLister = new ZipRawLister(file.getUri());
             return rawLister.getFileList();
        }

        public void run(){
            if(mTarget==null|| mToExtract ==null)
                return;
            try {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onStart();
                    }
                });
                //retrieving full list
                List <MetaFile2> toCompressTmp = new ArrayList<>(mToExtract);
                final List <MetaFile2> toCopy = new ArrayList<>();
                for(MetaFile2 mf2 : toCompressTmp){
                    List<MetaFile2> mfs2= listZipFile(mf2);
                    if(mfs2!=null)
                        toCopy.addAll(mfs2);

                }

                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener  .onFilesListUpdate(toCopy, toCopy);
                    }
                });
                mCopyEngine = new CopyCutEngine(mContext);
                mCopyEngine.setListener(mListener);
                mCopyEngine.copy(toCopy, mTarget, false);




            } catch (final Exception e) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onFatalError(e);
                    }
                });
                e.printStackTrace();
            }

        }
        public void set(List<MetaFile2> toCompress, Uri target){
            mToExtract = toCompress;
            mTarget = target;

        }
        // nice way to close things that might be null
        void closeSilently(Closeable closeme) {
            if (closeme == null) return;
            try {
                closeme.close();
            } catch (IOException e) {
                // silence
            }
        }
    }


}
