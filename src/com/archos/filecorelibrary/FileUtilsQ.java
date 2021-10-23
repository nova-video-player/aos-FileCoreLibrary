package com.archos.filecorelibrary;

import android.app.PendingIntent;
import android.app.RecoverableSecurityException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.core.content.FileProvider;

import com.archos.filecorelibrary.jcifs.JcifsUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

/**
 * 9th July 2021.
 * <p>
 * A class to read write external shared storage for android R.
 * Since Android 11 you can only access the android specified directories such as
 * DCIM, Download, Documents, Pictures, Movies, Music etc.
 * <p>
 * This class is just for an example class.
 *
 * @author <a href="https://github.com/fiftyonemoon">hardkgosai</a>.
 * @since 1.0.3.2
 */
public class FileUtilsQ {

    private static final Logger log = LoggerFactory.getLogger(FileUtilsQ.class);

    private static Context mContext;

    private static volatile FileUtilsQ sInstance;

    private static ActivityResultLauncher<IntentSenderRequest> mDeleteLauncher;

    public static void setDeleteLauncher(ActivityResultLauncher<IntentSenderRequest> launcher) {
        mDeleteLauncher = launcher;
    }

    public static ActivityResultLauncher<IntentSenderRequest> getDeleteLauncher() {
        return mDeleteLauncher;
    }

    // get the instance, context is used for initial context injection
    public static FileUtilsQ getInstance(Context context) {
        if (context == null) log.warn("getInstance: context passed is null!!!");
        else if (mContext == null) mContext = context;
        if (sInstance == null) {
            synchronized(FileUtilsQ.class) {
                if (sInstance == null) sInstance = new FileUtilsQ(context.getApplicationContext());
            }
        }
        return sInstance;
    }

    /** may return null but no Context required */
    public static FileUtilsQ peekInstance() {
        return sInstance;
    }

    public FileUtilsQ(Context context) {
        mContext = context;
    }

    public FileUtilsQ with(Context context) {
        mContext = context;
        return this;
    }

    /**
     * Create new media uri.
     */
    public Uri create(String directory, String filename, String mimetype) {

        ContentResolver contentResolver = mContext.getContentResolver();

        ContentValues contentValues = new ContentValues();

        //Set filename, if you don't system automatically use current timestamp as name
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename);

        //Set mimetype if you want
        contentValues.put(MediaStore.MediaColumns.MIME_TYPE, mimetype);

        //To create folder in Android directories use below code
        //pass your folder path here, it will create new folder inside directory
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, directory);
        }

        //pass new ContentValues() for no values.
        //Specified uri will save object automatically in android specified directories.
        //ex. MediaStore.Images.Media.EXTERNAL_CONTENT_URI will save object into android Pictures directory.
        //ex. MediaStore.Videos.Media.EXTERNAL_CONTENT_URI will save object into android Movies directory.
        //if content values not provided, system will automatically add values after object was written.
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
    }

    /**
     * Delete file.
     * <p>
     * If {@link ContentResolver} failed to delete the file, use trick,
     * SDK version is >= 29(Q)? use {@link SecurityException} and again request for delete.
     * SDK version is >= 30(R)? use {@link MediaStore#createDeleteRequest(ContentResolver, Collection)}.
     */
    public static Boolean delete(ActivityResultLauncher<IntentSenderRequest> launcher, Uri uri) {

        if (uri == null) return true;
        boolean isSuccessful = false;
        ContentResolver contentResolver = mContext.getContentResolver();

        try {
            // delete object using resolver
            log.debug("delete: uri " + uri);
            contentResolver.delete(uri, null, null);
            isSuccessful = true;
        } catch (SecurityException e) {
            PendingIntent pendingIntent = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                log.debug("delete: SecurityException Android >= R");
                ArrayList<Uri> collection = new ArrayList<>();
                collection.add(uri);
                pendingIntent = MediaStore.createDeleteRequest(contentResolver, collection);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                log.debug("delete: SecurityException Q<=Android<R");
                // if exception is recoverable then again send delete request using intent
                if (e instanceof RecoverableSecurityException) {
                    log.debug("delete: RecoverableSecurityException");
                    RecoverableSecurityException exception = (RecoverableSecurityException) e;
                    pendingIntent = exception.getUserAction().getActionIntent();
                }
            }
            if (pendingIntent != null) {
                log.debug("delete: pending intent not null");
                IntentSender sender = pendingIntent.getIntentSender();
                IntentSenderRequest request = new IntentSenderRequest.Builder(sender).build();
                launcher.launch(request);
                // TOTO MARC isSuccessful =
            }
        }

        return isSuccessful;

    }

    /**
     * Rename file.
     *
     * @param uri    - filepath
     * @param rename - the name you want to replace with original.
     */
    public void rename(Uri uri, String rename) {

        //create content values with new name and update
        ContentValues contentValues = new ContentValues();
        contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, rename);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            mContext.getContentResolver().update(uri, contentValues, null);
        }
    }

    /**
     * Duplicate file.
     *
     * @param uri - filepath.
     */
    public Uri duplicate(Uri uri) {

        ContentResolver contentResolver = mContext.getContentResolver();

        Uri output = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, new ContentValues());

        String input = getPathFromUri(uri);

        try (InputStream inputStream = new FileInputStream(input)) { //input stream

            OutputStream out = contentResolver.openOutputStream(output); //output stream

            byte[] buf = new byte[1024];
            int len;
            while ((len = inputStream.read(buf)) > 0) {
                out.write(buf, 0, len); //write input file data to output file
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return output;
    }

    /**
     * Get file path from uri.
     */
    private String getPathFromUri(Uri uri) {
        Cursor cursor = mContext.getContentResolver().query(uri, null, null, null, null);
        String text = null;
        if (cursor.moveToNext()) {
            text = cursor.getString(cursor.getColumnIndex(MediaStore.MediaColumns.DATA));
        }
        cursor.close();
        return text;
    }

    private static Uri contentUri;

    public static void scanFile(Uri uri) {
        MediaScannerConnection.scanFile(mContext, new String[]{ uri.getPath() },
                null, // mimetypes
                new MediaScannerConnection.OnScanCompletedListener() {
                    @Override
                    public void onScanCompleted(String path, Uri uri) {
                        // uri is in format content://media/external_primary/video/media/##
                        contentUri = uri;
                        log.debug("scanFile: contentUri " + contentUri + " for path " + path);
                    }
                });
    }

    public static boolean isVideoFile(Uri uri) {
        if (uri == null) return false;
        String uriPath = uri.getPath().toLowerCase(Locale.ROOT);
        String mimeType = URLConnection.guessContentTypeFromName(uriPath);
        return mimeType != null && mimeType.startsWith("video");
    }

    public static Uri getContentUri(Uri uri) {
        contentUri = null;
        // several methods https://stackoverflow.com/questions/7305504/convert-file-uri-to-content-uri

        File fileToDelete = new File(uri.getPath());
        if (!fileToDelete.exists()) {
            log.debug("getContentUri: file " + uri + " does not exist: easy job!");
            return null;
        } else {
            log.debug("getContentUri: file " + uri + " exists");
        }

        // TODO MARC converge on single method that works everywhere
        // NOK log.debug("getContentUri: Uri.parse " + Uri.parse(uri.getPath()));
        // NOK log.debug("getContentUri: Uri.fromFile " + Uri.fromFile(new File(uri.getPath())));

        ContentResolver contentResolver = mContext.getContentResolver();
        boolean isVideo = isVideoFile(uri);
        Uri mediaUri;
        Cursor cursor;
        if (isVideo) {
            // content://media/external/file/## uri
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mediaUri = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL);
            } else {
                mediaUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
            }
            //mediaUri = Uri.parse("content://media/external_primary/videos/media");
            cursor = contentResolver.query(mediaUri,
                    new String[] { MediaStore.Video.Media._ID },
                    MediaStore.Video.Media.DATA + "=?",
                    new String[] {uri.getPath()}, null);
        } else {
            // content://media/external/file/## uri
            mediaUri = MediaStore.Files.getContentUri("external");
            cursor = contentResolver.query(mediaUri,
                    new String[] { MediaStore.MediaColumns._ID },
                    MediaStore.MediaColumns.DATA + "=?",
                    new String[] {uri.getPath()}, null);
        }
        int id;
        if (cursor != null && cursor.moveToFirst()) {
            if (isVideo) {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.Video.VideoColumns._ID));
                // content://media/external/file/## uri
                contentUri = Uri.withAppendedPath(mediaUri, "" + id);
            } else {
                id = cursor.getInt(cursor.getColumnIndex(MediaStore.MediaColumns._ID));
                // content://media/external/file/## uri
                contentUri = MediaStore.Files.getContentUri("external",id);
            }
            cursor.close();
        }
        log.debug("getContentUri: contentResolver " + contentUri);

        /*
        // provides content://org.courville.nova.provider/external_files/filepath --> not working on phones
        contentUri = null;
        contentUri = FileProvider.getUriForFile(mContext, mContext.getApplicationContext().getPackageName() + ".provider", new File(uri.getPath()));
        log.debug("getContentUri: fileProvider provides contentUri " + contentUri);
         */

        if (false) {
            // this is the universal way but is asynchronous and requires to rework the logic...
            if (contentUri == null || !contentUri.getScheme().startsWith("content")) {
                // this one provides content://media/external_primary/video/media/## but is asynchronous --> should do observer?
                scanFile(uri);
                log.debug("getContentUri: mediaScannerScanFile provides contentUri " + contentUri);
            }
        }

        return contentUri;
    }
}