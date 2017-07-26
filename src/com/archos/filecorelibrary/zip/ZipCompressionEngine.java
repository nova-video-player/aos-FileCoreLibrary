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

import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.archos.filecorelibrary.FileEditorFactory;
import com.archos.filecorelibrary.MetaFile2;
import com.archos.filecorelibrary.OperationEngineListener;
import com.archos.filecorelibrary.Utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by alexandre on 20/07/15.
 */
public class ZipCompressionEngine {
    private final Handler mUiHandler;
    private final OperationEngineListener mListener;
    private CompressThread mCompressThread;
    private boolean mHasToStop;




    public ZipCompressionEngine(OperationEngineListener listener){
        mListener = listener;
        mUiHandler = new Handler(Looper.getMainLooper());

    }

    public void stop() {
        mHasToStop = true;
    }
    public void compress(List<MetaFile2> toCompress, Uri target){
        if(mCompressThread!=null&&mCompressThread.isAlive())
            return;
        mHasToStop = false;
        mCompressThread = new CompressThread();
        mCompressThread.set(toCompress, target);
        mCompressThread.start();
    }
    public void abort(){
        mHasToStop = true;
    }

    private class CompressThread extends  Thread{
        private List<MetaFile2> mToCompress;
        private Uri mTarget;
        private long mTotalSize;
        private String mRootPath;
        private int mRootOffset;


        private void getDirectoryInfo(MetaFile2 file) throws Exception {

            List<MetaFile2> files = file.getRawListerInstance().getFileList();
            if (files != null) {

                for (MetaFile2 f : files) {
                    if(mHasToStop)
                        return;
                    mToCompress.add(f);

                    if (f.isDirectory()) {
                        getDirectoryInfo(f);
                    } else {
                        mTotalSize += f.length();
                    }
                }

            }
        }

        public void run(){
            if(mTarget==null||mToCompress==null)
                return;
            try {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onStart();
                    }
                });
                mRootPath = Utils.getParentUrl(mToCompress.get(0).getUri().toString());
                mRootOffset = mRootPath.length();
                //retrieving full list
                List <MetaFile2> toCompressTmp = new ArrayList<>(mToCompress);
                for(MetaFile2 mf2 : toCompressTmp){
                    if(mf2.isDirectory())
                        getDirectoryInfo(mf2);

                }
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onFilesListUpdate(mToCompress,mToCompress);
                    }
                });

                ZipEntry entry;
                String path;
                InputStream fis;
                ZipOutputStream zos = new ZipOutputStream(FileEditorFactory.getFileEditorForUrl(mTarget, null).getOutputStream());
                int i =0;
                for(MetaFile2 mf2 : mToCompress) {
                    final int progress = i;
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onProgress(progress, -1, progress, -1, -1, -1.0);
                        }
                    });

                    if (mf2.isDirectory()) {
                        entry = new ZipEntry(mf2.getUri().toString().substring(mRootOffset).concat("/"));
                        entry.setTime(mf2.lastModified());
                        zos.putNextEntry(entry);
                        zos.closeEntry();
                    } else {
                        entry = new ZipEntry(mf2.getUri().toString().substring(mRootOffset));
                        int count = 0;
                        byte[] bytes = new byte[1024];
                        fis = mf2.getFileEditorInstance(null).getInputStream();
                        entry.setSize(mf2.length());
                        entry.setTime(mf2.lastModified());
                        zos.putNextEntry(entry);

                        while ((count = fis.read(bytes)) > 0&&!mHasToStop) {
                            zos.write(bytes, 0, count);
                        }
                        zos.closeEntry();
                        closeSilently(fis);
                    }
                    i++;
                    if(mHasToStop)
                        break;
                }
                if(mHasToStop){
                    FileEditorFactory.getFileEditorForUrl(mTarget, null).delete(); // delete zip
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mListener.onCanceled();
                        }
                    });
                    return;

                }
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mListener.onEnd();
                    }
                });

                closeSilently(zos);
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
            mToCompress = toCompress;
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
