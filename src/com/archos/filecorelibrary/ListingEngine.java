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

import com.archos.filecorelibrary.localstorage.JavaFile2;

import java.util.List;
import java.util.Locale;

/**
 * The interface to list MetaFiles to display to the user.
 * The goal it to put the result directly in a ListView Adapter, hence it includes filtering and listing
 * It is asynchronous: result and errors are returned via the ListingEngine.Listener interface.
 * The input is usually an Uri given in the constructor of the child class (but could be something else for some kind of child class).
 * @author vapillon
 *
 */
public abstract class ListingEngine {

    protected Listener mListener;
    protected String[] mMimeTypeFilter;
    protected String[] mExtensionFilter;
    protected Handler mUiHandler;
    protected Context mContext;
    protected SortOrder mSortOrder;
    private long mListingTimeOutMs;
    private boolean mKeepHiddenFiles;

    /**
     * error codes
     */
    public static enum ErrorEnum{
        ERROR_HOST_NOT_FOUND,
        ERROR_UNKNOWN_HOST,
        ERROR_FILE_NOT_FOUND,
        ERROR_NO_PERMISSION,
        ERROR_AUTHENTICATION,
        ERROR_UNKNOWN,
        ERROR_UPNP_DEVICE_NOT_FOUND,
        ERROR_UPNP_BROWSE_ERROR,
        SUCCESS
    }

    public static int getErrorStringResId(ErrorEnum error) {
        switch (error) {
            case ERROR_HOST_NOT_FOUND:
                return R.string.error_host_not_found;
            case ERROR_UNKNOWN_HOST:
                return R.string.error_unknown_host;
            case ERROR_FILE_NOT_FOUND:
                return R.string.error_file_not_found;
            case ERROR_NO_PERMISSION:
                return R.string.error_no_permission;
            case ERROR_AUTHENTICATION:
                return R.string.error_credentials;
            case ERROR_UNKNOWN:
            default:
                return R.string.error_listing;
        }
    }

    /**
     * sort order
     */
    public static enum SortOrder {
        SORT_BY_URI_ASC,
        SORT_BY_URI_DESC,
        SORT_BY_NAME_ASC,
        SORT_BY_NAME_DESC,
        SORT_BY_SIZE_ASC,
        SORT_BY_SIZE_DESC,
        SORT_BY_DATE_ASC,
        SORT_BY_DATE_DESC
    }

    /**
     * True when the time-out has actually occurred
     */
    private boolean mTimeOutHasOccurred = false;

    public interface Listener {
        /**
         * Caution: onListingStart is called AFTER start() returns (because it is "posted" to the UI thread)
         */
        public void onListingStart();

        public void onListingUpdate(List<? extends MetaFile2> files);

        /**
         * The listing is finished
         */
        public void onListingEnd();

        /**
         * Time out occurred, onListingUpdate() will not be called, onListingEnd() will be called
         */
        public void onListingTimeOut();

        /**
         * In case the listing requires credentials
         * The listing is stopped in that case and a new listing with proper credentials must be done from scratch
         * @param e: optional, may be null
         */
        public void onCredentialRequired(Exception e);

        /**
         * Reports an error that stops the listing
         * @param e: optional, may be null
         */
        public void onListingFatalError(Exception e, ErrorEnum errorCode);

        void onListingFileInfoUpdate(Uri uri, MetaFile2 metaFile2);
    }

    public ListingEngine(Context context) {
        mContext = context;
        mUiHandler = new Handler(Looper.getMainLooper());
        mSortOrder = SortOrder.SORT_BY_URI_ASC;
    }

    /**
     * All the listener methods are called asynchronously on the UI thread (onListingStart() is called AFTER start() returns)
     * @param listener
     */
    public final void setListener(Listener listener) {
        mListener = listener;
    }

    public void setFilter(String[] mimeTypeFilter, String[] extensionFilter) {
        mMimeTypeFilter = mimeTypeFilter;
        mExtensionFilter = extensionFilter;
    }

    /**
     * Set the listing time out
     * @param listingTimeOutMs: time out in milliseconds
     */
    public void setListingTimeOut(long listingTimeOutMs) {
        mListingTimeOutMs = listingTimeOutMs;
    }

    public void setSortOrder(SortOrder order) {
        mSortOrder = order;
    }

    public void setKeepHiddenFiles(boolean keep){
        mKeepHiddenFiles = keep;
    }

    public abstract void start();

    public abstract void abort();

    protected boolean keepFile(String filename) {
        // don't keep a hidden file
        if (filename.startsWith(".")&&!mKeepHiddenFiles) {
            return false;
        }
        // keep file if filter list contains its extension
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.US);
        boolean extensionFiltered = mExtensionFilter != null && mExtensionFilter.length > 0;
        if (extensionFiltered) {
            for (String filt : mExtensionFilter) {
                if (extension != null && extension.equalsIgnoreCase(filt) && !filt.isEmpty()) {
                    return true;
                }
            }
        }
        // keep file if filter list contains start of its mime type
        String mimeType = MimeUtils.guessMimeTypeFromExtension(extension);
        boolean mimeTypeFiltered = mMimeTypeFilter != null && mMimeTypeFilter.length > 0;
        if (mimeTypeFiltered) {
            for (String filt : mMimeTypeFilter) {
                if (mimeType != null && mimeType.startsWith(filt) && !filt.isEmpty()) {
                    return true;
                }
            }
        }
        if (mimeTypeFiltered || extensionFiltered) {
            return false;
        } else {
            return true;
        }
    }

    protected boolean keepDirectory(String filename) {
        // don't keep a hidden directory
        if (filename.startsWith(".")) {
            return false;
        }
        // don't keep a weird directory
        if (filename.equalsIgnoreCase("IPC$/") || filename.equalsIgnoreCase("print$/")) {
            return false;
        }
        return true;
    }

    protected void preLaunchTimeOut() {
        if (mListingTimeOutMs>0) {
            mUiHandler.postDelayed(mTimeOutRunnable, mListingTimeOutMs);
        }
    }

    /**
     * To be called as soon as we get the results, to cancel the delayed time-out message
     */
    protected void noTimeOut() {
        mTimeOutHasOccurred = false;
        mUiHandler.removeCallbacks(mTimeOutRunnable);
    }

    protected boolean timeOutHasOccurred() {
        return mTimeOutHasOccurred;
    }

    final Runnable mTimeOutRunnable = new Runnable() {
        public void run() {
            mTimeOutHasOccurred = true;
            if (mListener != null) {
                mListener.onListingTimeOut();
            }
        }
    };
}
