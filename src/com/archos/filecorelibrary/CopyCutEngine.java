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
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CopyCutEngine {
    private final Context mContext;
    private Handler mUiHandler;
    private CopyThread mCopyThread;
    private OperationEngineListener mListener;
    private boolean mHasToStop;
    private static final int MAX_COUNT = 32768;
    private String mFileCopyString;
    private String mFirstCopyPattern; // Full pattern for the first copy
    private String mCopyPatternLeft; // Part of the pattern before the index
    private String mCopyPatternRight; // Part of the pattern after the index
    private String mTargetFilePrefix;


    /**
     * All the listener methods are called asynchronously on the UI thread (onListingStart() is called AFTER start() returns)
     * @param listener
     */
    public final void setListener(OperationEngineListener listener) {
        mListener = listener;
    }
    public CopyCutEngine(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
        mFileCopyString = context.getString(R.string.file_copy_pattern);
        mFirstCopyPattern = " (" + mFileCopyString + ")";
        mCopyPatternLeft = " (" + mFileCopyString + " ";
        mCopyPatternRight = ")";
    }

    /**
     * Most of the time, we only have one target
     * 
     * @param sources
     * @param target
     */
    public void copy(List<MetaFile2> sources, Uri target, boolean overwrite){

        if(mCopyThread==null || !mCopyThread.isAlive()){
            mCopyThread = new CopyThread();
            mHasToStop = false;
            mCopyThread.setCopyLists(sources,target,false, overwrite);
            mCopyThread.start();
        }
    }

    public void copyUri(List<Uri> sources, Uri target, boolean overwrite){

        if(mCopyThread==null || !mCopyThread.isAlive()){
            mCopyThread = new CopyThread();
            mHasToStop = false;
            mCopyThread.setCopyLists(sources,target, overwrite);
            mCopyThread.start();
        }
    }

    public void cut(ArrayList<MetaFile2> sources, Uri target, boolean overwrite) {
        if(mCopyThread==null || !mCopyThread.isAlive()){
            mCopyThread = new CopyThread();
            mHasToStop = false;
            mCopyThread.setCopyLists(sources,target,true, overwrite);
            mCopyThread.start();
        }
    }

    public void stop() {
        mHasToStop = true;
    }

    public void setAllTargetFilesShouldStartWithString(String s) {
        mTargetFilePrefix = s;
    }


    final class CopyThread extends Thread {
        private List<MetaFile2> mSources;
        private List<Uri> mSourcesUri;
        private HashMap<MetaFile2, Uri> mSourceTarget;
        private int mTotalSize = 0;
        private Uri mTargetDirectory;
        private boolean mCut;
        private boolean mOverwrite;
        /* to delete folder on file cut, we need to keep parent of each metafile and children of each metafile because folders
        are not recursively copied (first we retrieve every children, then we copy each one of them as singular files)
        these maps will be used to check at the end of each file cut if parent folder is empty.
        we need two maps to first retrieve a child's parent  and check if this parent has other children (better performance than iterating, I guess)
        */
        private HashMap<MetaFile2, List<MetaFile2>> parents; // key : parent, value : children
        private HashMap<MetaFile2, MetaFile2>children; // key : a child file, value : its parent
        private long mLastUpdate = 0;


        private Uri getNextCopyUri(MetaFile2 toCopy, List<MetaFile2> inTargetDirectory, Uri directory) {
            // Extract the filename and the extension from the complete path
            String fullName = toCopy.getName();
            //add prefix when needed
            if(mTargetFilePrefix!=null&&!fullName.startsWith(mTargetFilePrefix)){
                fullName = mTargetFilePrefix+fullName;
            }
            if(mOverwrite)
                return Utils.buildChildUri(directory, fullName);
            String name; // myvideo
            String extension; // avi
            // Unused.

            //check if already in target
            boolean isInTarget = false;
            for(MetaFile2 target : inTargetDirectory){
                if(target.getName().equals(fullName)){
                    isInTarget = true;
                    break;
                }
            }
            if(!isInTarget){
                return Utils.buildChildUri(directory, fullName);
            }
            int extensionPos = fullName.lastIndexOf('.');
            if (extensionPos >= 0) {
                name = fullName.substring(0, extensionPos);
                extension = fullName.substring(extensionPos + 1);
            } else {
                name = fullName;
                extension = "";
            }

            // Get the base name of the file to copy:
            // - if the file is itself a copy of another file => use the name of
            // the original file
            // - use the current name otherwise
            String baseName = name;
            if (name.endsWith(mFirstCopyPattern)) {
                baseName = name.substring(0, name.length() - mFirstCopyPattern.length());
            } else if (name.endsWith(mCopyPatternRight)) {
                // Check if we can find the " (copy NN)" pattern
                int templatePos = name.lastIndexOf(mCopyPatternLeft);
                if (templatePos >= 0) {
                    baseName = name.substring(0, templatePos);
                }
            }

            // Check if there are already some copies of the file in the current
            // folder
            // and retrieve the first available index for the suffix to add to
            // the filename
            int copyIndex = getNextCopyIndex(inTargetDirectory, baseName, extension);

            // Build the new filename by appending a suffix to the current
            // filename
            String suffix;
            if (copyIndex == 1) {
                // First copy => add a " (copy)" suffix
                suffix = mFirstCopyPattern;
            } else {
                // Other copies => add a " (copy NN)" suffix
                suffix = mCopyPatternLeft + copyIndex + mCopyPatternRight;
            }
            if (extension.isEmpty()) {
                return Utils.buildChildUri(directory, baseName + suffix);
            }

            return Utils.buildChildUri(directory,baseName + suffix + "." + extension);
        }

        private int getNextCopyIndex(List<MetaFile2> inTargetDirectory, String originalName,
                                     String originalExtension) {
            int maxCopyIndex = 0;
            // Scan the contents of the current folder
            for (MetaFile2 file : inTargetDirectory) {
                int index = 0;
                // Extract the filename and the extension from the complete path
                String extension;
                int extensionPos = file.getName().lastIndexOf('.');
                if (extensionPos >= 0) {
                    extension = file.getName().substring(extensionPos + 1);
                } else {
                    extension = "";
                }
                if(extension==null)
                    extension="";
                // We only need to compare the filenames if the extension is the
                // same
                if (extension.equals(originalExtension)) {
                        String fileName;
                        if (extension.isEmpty()) {
                        fileName = file.getName();
                    } else {
                        fileName = file.getName().substring(0, file.getName().length()
                                - extension.length() - 1);
                    }
                    if (fileName.startsWith(originalName)) {
                        // Check if there is a known suffix at the end of the
                        // filename
                        if (fileName.endsWith(mFirstCopyPattern)) {
                            // This is the first copy of the original file
                            index = 1;
                        } else if (fileName.endsWith(mCopyPatternRight)) {
                            // This could be a copy of the original file
                            // => check if we can find the " (copy NN)" pattern
                            // and extract NN then
                            int templatePos = fileName.lastIndexOf(mCopyPatternLeft);
                            if (templatePos >= 0) {
                                String indexString = fileName.substring(templatePos
                                        + mCopyPatternLeft.length(), fileName.length() - 1);
                                index = Integer.parseInt(indexString);
                            }
                        }
                    }
                }

                if (maxCopyIndex < index) {
                    // We found a copy with a higher index => remember it
                    maxCopyIndex = index;
                }
            }
            return (maxCopyIndex + 1);
        }

        /**
         * List directories, make target lists, get information
         * @param file
         * @param target
         * @throws Session.AuthenticationException
         * @throws IOException
         * @throws SftpException
         * @throws JSchException
         *
         * return size
         */

        private long getDirectoryInfo(MetaFile2 file, Uri target, List<MetaFile2> filesToCopy) throws Exception {
            long size = 0;
            List<MetaFile2> files = file.getRawListerInstance().getFileList();
            parents.put(file, files);
            if (files != null) {

                for (MetaFile2 f : files) {
                    if(mHasToStop)
                        return -1;
                    filesToCopy.add(f);
                    Uri newTarget = Uri.withAppendedPath(target, f.getName());
                    mSourceTarget.put(f,newTarget);
                    children.put(f, file);
                    if (f.isDirectory()) {
                        size += getDirectoryInfo(f,newTarget, filesToCopy);
                    } else {
                        size += f.length();
                        mTotalSize += f.length();
                    }
                }

            }
            return size;
        }

        /**
         * Returns average copy speed in bytes/second.
         */
        private double getCopySpeed(long position, long startTime) {
            long elapsedTime = System.currentTimeMillis() - startTime;
            return 1000.0 * position / elapsedTime;
        }

        public long copy(final MetaFile2 source, final Uri target, final int currentFile, final int currentRootFile, long rootProgress, long totalProgress) throws Exception {
            FileEditor targetEditor = FileEditorFactory.getFileEditorForUrl(target,mContext);
            if (source.isDirectory()) {
                targetEditor.mkdir();
            }
            else {
                FileEditor sourceEditor = FileEditorFactory.getFileEditorForUrl(source.getUri(),mContext);
                OutputStream out = targetEditor.getOutputStream();
                InputStream in = sourceEditor.getInputStream();
                if (in != null && out != null) {
                    long position = 0;
                    long startTime = System.currentTimeMillis();
                    boolean isNetworkFileCopy = !Utils.isLocal(target) || !Utils.isLocal(source.getUri());
                    byte buf[] = new byte[MAX_COUNT];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        if(mHasToStop)
                            break;
                        out.write(buf, 0, len);
                        position += (long)len;
                        totalProgress +=(long)len;
                        rootProgress += (long)len;
                        final long finalPosition = position;
                        final long finalTotalProgress = totalProgress;
                        final long finalRootProgress = rootProgress;
                        final double currentSpeed = isNetworkFileCopy ? getCopySpeed(position, startTime) : -1.0;
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if(mListener != null){
                                    mListener.onProgress(currentFile, finalPosition, currentRootFile, finalRootProgress, finalTotalProgress, currentSpeed);
                                }
                            }
                        });

                    }
                    out.close();
                    in.close();
                    if(mHasToStop){
                        targetEditor.delete();
                    }else{
                        if(Utils.isLocal(target)) {
                            Uri toIndex = target;
                            if (toIndex.getScheme() == null)
                                toIndex = Uri.parse("file://" + toIndex.toString());
                            Intent scanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                            scanIntent.setData(toIndex);
                            mContext.sendBroadcast(scanIntent);
                        }
                        mUiHandler.post(new Runnable() {
                            @Override
                            public void run() {
                                if (mListener != null) {
                                    mListener.onSuccess(target);
                                }
                            }
                        });
                    }

                    return position;
                }
            }
            return 0;
        }

        /**
         * will delete parent folders when empty
         *
         * @param justDeletedFile file that has already been deleted
         * @throws Exception
         */

        private void invertedDelete(MetaFile2 justDeletedFile) throws Exception {
            if(children.containsKey(justDeletedFile)) {
                // this file isn't in parent anymore
                parents.get(children.get(justDeletedFile)).remove(justDeletedFile);
                // we check if parent is empty, if it is, we delete it
                if (parents.get(children.get(justDeletedFile)).size() == 0) {
                    // we delete parent
                    children.get(justDeletedFile).getFileEditorInstance(mContext).delete();
                    // we check parent of parent
                    invertedDelete(children.get(justDeletedFile));
                }
                children.remove(justDeletedFile);
            }
        }
        public void run(){
            boolean cut = mCut;
            int i = 0;
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    if(mListener!=null)
                        mListener.onStart();
                }
            });
            mTotalSize=0;
            try {

                    //first we check if we have metafiles, if we don't, we retrieve them
                    if(mSources==null&&mSourcesUri!=null){
                        mSources = new ArrayList<MetaFile2>();
                       for(Uri uri : mSourcesUri){
                           MetaFile2 mf = MetaFile2Factory.getMetaFileForUrl(uri);
                           if(mf!=null){
                               mSources.add(mf);
                           }
                       }
                    }
                //we check if target directory exists
                FileEditor fe = FileEditorFactory.getFileEditorForUrl(mTargetDirectory,mContext);
                if(!fe.exists())
                    fe.mkdir();
                // we make a list of targets uri based on what is already in targetdirectory
                List<MetaFile2> files = RawListerFactory.getRawListerForUrl(mTargetDirectory).getFileList();

                //infolder is useful to avoid conflict when copying
                    /*
                        for example:
                        Alarm
                        Alarm (copy)
                        has to be copied in the same root folder
                        the result we want is

                        Alarm
                        Alarm (copy)
                        Alarm (copy 2)
                        Alarm (copy 3)

                        so we have to update target directory files list

                     */

                    for(MetaFile2 mf : mSources){
                        if(mHasToStop)
                            break;
                        Uri nextTarget = getNextCopyUri(mf, files, mTargetDirectory);
                        mSourceTarget.put(mf, nextTarget);

                    }

                ArrayList<MetaFile2> toRetrieve = new ArrayList<MetaFile2>();
                toRetrieve.addAll(mSources);

                long totalSize = 0;
                long totalProgress = 0;
                final List<MetaFile2> rootFiles = new ArrayList<>();
                final List<MetaFile2> filesToCopy = new ArrayList<>();
                for(MetaFile2 source : toRetrieve){
                    if(mHasToStop)
                        break;
                    filesToCopy.add(source);
                    if(source.isDirectory()) {
                        source.setLength(getDirectoryInfo(source, mSourceTarget.get(source), filesToCopy));
                    }
                    rootFiles.add(source);
                    i++;
                }
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if(mListener != null){
                            mListener.onFilesListUpdate(filesToCopy,rootFiles);
                        }
                    }
                });

                i = 0;
                long rootProgress = 0;
                int currentRootFile = -1;
                for(final MetaFile2 source : filesToCopy){
                    if(rootFiles.contains(source)) {//changing root file
                        currentRootFile++;
                        rootProgress = 0;
                    }
                    final int currentFile = i;
                    final long finalTotalProgress = totalProgress;
                    final int finalCurrentRootFile = currentRootFile;
                    final long finalRootProgress = rootProgress;
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener != null){
                                mListener.onProgress(currentFile, 0, finalCurrentRootFile, finalRootProgress, finalTotalProgress, -1.0);
                            }
                        }
                    });

                    if(mHasToStop)
                        break;
                    boolean moveSuccess = false;
                    if(cut&&source.isFile()){ //first, we need to try a fast move
                        try {
                            long tmpProgress = source.length();
                            moveSuccess = source.getFileEditorInstance(mContext).move(mSourceTarget.get(source));
                            if(moveSuccess) {
                                totalProgress += tmpProgress;
                                // we don't delete folders when there are still files to copy inside
                                invertedDelete(source);
                            }

                        }catch(Exception e){

                        }
                    }
                    if(!cut||!moveSuccess) { // if move didn't succeeded, we try to copy then to delete
                        long progress = copy(source, mSourceTarget.get(source), i, currentRootFile, rootProgress, totalProgress);
                        totalProgress += progress;
                        rootProgress += progress;
                        if (!mHasToStop && cut) {
                            if (source.isFile() || parents.get(source).size() == 0) { // we don't delete folders when there are still files to copy inside
                                source.getFileEditorInstance(mContext).delete();
                                invertedDelete(source);
                            }
                        }
                    }
                    final long finalTotalProgress2 = totalProgress;
                    final int finalCurrentRootFile2 = currentRootFile;
                    final long finalRootProgress2 = rootProgress;
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener != null){
                                mListener.onProgress(currentFile, -1, finalCurrentRootFile2, finalRootProgress2, finalTotalProgress2, -1.0); //-1 means finished
                            }
                        }
                    });
                    i++;
                }
                if(mHasToStop){
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            if(mListener!=null)
                                mListener.onCanceled();
                        }
                    });
                }
                else{
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

        /**
         * if "targets" is null, "targetDirectory" will be the directory where files will be put to
         * @param sources
         * @param targetDirectory
         * @param cut
         */
        public void setCopyLists(List<MetaFile2> sources, Uri targetDirectory, boolean cut,boolean overwrite) {
            // initializing variables
            children = new HashMap<MetaFile2, MetaFile2>();
            parents = new HashMap<MetaFile2, List<MetaFile2>>();
            mSourceTarget = new HashMap<MetaFile2, Uri>();
            mSources = sources;
            mTargetDirectory = targetDirectory;
            mCut = cut;
            mOverwrite = overwrite;
        }

        public void setCopyLists(List<Uri> sources, Uri targetDirectory,boolean overwrite) {
            // initializing variables
            children = new HashMap<MetaFile2, MetaFile2>();
            parents = new HashMap<MetaFile2, List<MetaFile2>>();
            mSourceTarget = new HashMap<MetaFile2, Uri>();
            mSources = null;
            mSourcesUri = sources;
            mOverwrite = overwrite;
            mTargetDirectory = targetDirectory;
            mCut = false;
        }
    }

}
