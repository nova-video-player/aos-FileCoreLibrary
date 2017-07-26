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

import android.net.Uri;

import java.util.HashMap;
import java.util.List;

/**
 * Created by alexandre on 19/08/15.
 */
public interface OperationEngineListener {
    /**
     *
     */
    public void onStart();
    /**
     * Progress status of current copy
     * currentFile : current file in complete list of files to copy
     * currentFileProgress : its progress
     * currentRootFile : currently copying rootFile as first set in the engine
     * currentRootProgress : its progress
     *
     */
    public void onProgress(int currentFile, long currentFileProgress, int currentRootFile, long currentRootProgress, long totalProgress, double currentSpeed);
    /**
     * copy success for a particular file
     */
    public void onSuccess(Uri file);

    /**
     * send new file list
     *
     * copyingMetaFiles : every metafile being copied
     * rootMetaFiles : files as first set by user of the engine (for example : copy(List<Uri>) Uri -> Metafile2) associated with there total length
     *
     *
     */
    public void onFilesListUpdate(List<MetaFile2> copyingMetaFiles, List<MetaFile2> rootMetaFiles);
    /**
     * Copy is finished
     */
    public void onEnd();

    /**
     * When an error occurred
     */
    public void onFatalError(Exception e);

    /**
     * When action is canceled
     */
    public void onCanceled();
}
