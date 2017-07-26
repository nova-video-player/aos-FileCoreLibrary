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

package com.archos.filecorelibrary;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.List;

public class DeleteEngine {
    private final Context mContext;
    private Handler mUiHandler;
    private DeleteThread mDeleteThread;
    private OperationEngineListener mListener;
    private boolean mHasToStop;



    /**
     * All the listener methods are called asynchronously on the UI thread (onListingStart() is called AFTER start() returns)
     * @param listener
     */
    public final void setListener(OperationEngineListener listener) {
        mListener = listener;
    }
    public DeleteEngine(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
    }

    /**
     * Specify the list of files to delete and start deleting process
     * @param filestodelete
     * 
     */
    public void delete(List<MetaFile2> filestodelete){

        if(mDeleteThread==null || !mDeleteThread.isAlive()){
            mHasToStop = false;
            mDeleteThread = new DeleteThread();
            mDeleteThread.setDeleteList(filestodelete);
            mDeleteThread.start();
        }
    }

    /**
     * Specify the list of uri to delete and start deleting process
     * @param filestodelete
     *
     */
    public void deleteUri(List<Uri> filestodelete){

        if(mDeleteThread==null || !mDeleteThread.isAlive()){
            mHasToStop = false;
            mDeleteThread = new DeleteThread();
            mDeleteThread.setDeleteUriList(filestodelete);
            mDeleteThread.start();
        }
    }
    public void stop() {
        mHasToStop = true;

    }
    final class DeleteThread extends Thread{
        private List<MetaFile2> mSources;
        private List<Uri> mSourcesUri;

        public void run(){
            int i = 0;
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(mListener!=null)
                        mListener.onStart();
                }
            });
            try {
                if(mSources==null&&mSourcesUri!=null){
                    mSources = new ArrayList<MetaFile2>();
                    for(Uri uri : mSourcesUri){
                        MetaFile2 mf = MetaFile2Factory.getMetaFileForUrl(uri);
                        if(mf!=null)
                            mSources.add(mf);
                    }
                }
                if(mSources!=null ){
                    for(final MetaFile2 mf : mSources){
                        if(mHasToStop)
                        {
                            mUiHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    if(mListener!=null)
                                        mListener.onCanceled();
                                }
                            });
                            return;
                        }
                       FileEditor fe =  mf.getFileEditorInstance(mContext);
                       final int progress = i;
                       if(fe!=null) {
                           fe.delete();
                           mUiHandler.post(new Runnable() {
                               @Override
                               public void run() {
                                   if(mListener!=null)
                                       mListener.onSuccess(mf.getUri());
                               }
                           });
                       }
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mListener!=null)
                                    mListener.onProgress(progress, -1, progress, -1, -1, -1.0);
                            }
                        });
                       i++; 
                    }
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener!=null)
                                mListener.onEnd();
                        }
                    });
                }
            
            } catch (final Exception e) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mListener!=null)
                            mListener.onFatalError(e);
                    }
                });
            }  
        }
        public void setDeleteList(List<MetaFile2> filestodelete) {
            mSources = filestodelete;
        }
        public void setDeleteUriList(List<Uri> filestodelete) {
            mSourcesUri = filestodelete;
        }
    }


}
