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

package com.archos.filecorelibrary.samba;

import android.os.FileObserver;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class SambaConfigurationCache {
    public final static SambaConfigurationCache INSTANCE = new SambaConfigurationCache();

    private final HashMap<String, SambaSingleSetting> mCache = new HashMap<String, SambaSingleSetting>();
    private final FileValidChecker mChecker = new FileValidChecker(SambaConfiguration.configFile);

    private synchronized void checkCache() {
        if (mChecker.fileHasChanged()) {
            Log.d(SambaConfiguration.TAG, "Updating credentials cache");
            updateCache();
        }
    }

    private synchronized void updateCache() {
        mCache.clear();
        List<String> singleSettingList = SambaConfiguration.getSingleSettingListUncached();
        for (String section : singleSettingList) {
            SambaSingleSetting setting = SambaConfiguration.getSingleSettingUncached(section);
            if (setting != null)
                mCache.put(section, setting);
        }
    }

    public synchronized boolean sectionExist(String section) {
        checkCache();
        return mCache.containsKey(section);
    }

    public synchronized SambaSingleSetting getSection(String section) {
        checkCache();
        return mCache.get(section);
    }

    public synchronized LinkedList<String> getSingleSettingList() {
        checkCache();
        LinkedList<String> list = new LinkedList<String>();
        for (String section : mCache.keySet()) {
            list.add(section);
        }
        return list;
    }

    /** Checks for file changes, either by date or by using a FileObserver */
    private static class FileValidChecker {
        private final File mFile;
        private final String mFilePath;

        protected volatile FileObserver mObserver;
        protected volatile long mFileDate = -1;

        protected final AtomicBoolean mHasChanged = new AtomicBoolean(true);

        public FileValidChecker(String path) {
            mFilePath = path;
            mFile = new File(path);
            checkObserver();
        }

        public boolean fileHasChanged() {
            // if observer exists, use the bool updated by it
            if (mObserver != null) {
                return mHasChanged.getAndSet(false);
            }

            // otherwise check file date
            long fileDate = mFile.lastModified();
            // date > 0 => file exists now, start observer
            if (fileDate > 0)
                checkObserver();

            if (mFileDate != fileDate) {
                // date has changed -> true
                mFileDate = fileDate;
                return true;
            }

            return false;
        }

        private void checkObserver() {
            if (mObserver == null && mFile.exists()) {
                mObserver = getObserver();
                mObserver.startWatching();
            }
        }

        private static final int OBSERVER_FLAGS = FileObserver.MODIFY | FileObserver.CLOSE_WRITE | FileObserver.DELETE_SELF;

        private FileObserver getObserver() {
            return new FileObserver(mFilePath, OBSERVER_FLAGS) {
                @Override
                public void onEvent(int event, String path) {
                    // delete or modify invalidates the file
                    mHasChanged.set(true);
                    // deleting the file also invalidates the observer
                    if (event == FileObserver.DELETE_SELF) {
                        mObserver = null;
                        // invalidate file date
                        mFileDate = -1;
                    }
                }
            };
        }
    }
}
