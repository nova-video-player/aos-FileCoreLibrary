// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.filecorelibrary;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.system.Os;
import android.system.OsConstants;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.msfscc.fileinformation.FileStandardInformation;
import com.hierynomus.mssmb2.SMB2CreateDisposition;
import com.hierynomus.mssmb2.SMB2ShareAccess;
import java.io.IOException;
import java.util.EnumSet;
import net.schmizz.sshj.sftp.OpenMode;

/** Explicit metadata editing only. Never creates, truncates, appends, or retries writes. */
public abstract class RandomAccessMediaFile implements AutoCloseable {
    public abstract long length() throws Exception;
    public abstract int read(long offset, byte[] data, int start, int count) throws Exception;
    public abstract void write(long offset, byte[] data, int start, int count) throws Exception;
    public abstract void flush() throws Exception;
    public abstract void close() throws Exception;

    public final byte[] readFully(long offset, int count) throws Exception {
        if (offset < 0 || count < 0 || count > 1024 * 1024 || offset > length() - count)
            throw new IOException("Invalid metadata read range");
        byte[] data = new byte[count];
        for (int n = 0; n < count;) {
            int got = read(offset + n, data, n, count - n);
            if (got <= 0)
                throw new IOException("Incomplete metadata read");
            n += got;
        }
        return data;
    }

    public static RandomAccessMediaFile open(Context context, Uri uri) throws Exception {
        String scheme = uri.getScheme();
        if ("smb".equals(scheme) || "smbj".equals(scheme) || "sftp".equals(scheme) || "sshj".equals(scheme))
            NetworkInitialization.awaitReady(context);
        if ("smb".equals(scheme) || "smbj".equals(scheme)) {
            com.hierynomus.smbj.share.File file = SmbjUtils.getInstance(context).getSmbShare(uri).openFile(
                FileUtils.getFilePath(uri), EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_WRITE), null,
                EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ), SMB2CreateDisposition.FILE_OPEN, null);
            return new RandomAccessMediaFile() {
                public long length() {
                    return file.getFileInformation(FileStandardInformation.class).getEndOfFile();
                }
                public int read(long at, byte[] b, int off, int len) {
                    return file.read(b, at, off, len);
                }
                public void write(long at, byte[] b, int off, int len) throws Exception {
                    for (int done = 0; done < len;) {
                        long n = file.write(b, at + done, off + done, len - done);
                        if (n <= 0 || n > len - done)
                            throw new IOException("Incomplete SMB write");
                        done += (int) n;
                    }
                }
                public void flush() {
                    file.flush();
                }
                public void close() throws Exception {
                    file.close();
                }
            };
        }
        if ("sftp".equals(scheme) || "sshj".equals(scheme)) {
            // Use SSHJ's explicit handle API even when JSch is selected for playback.
            net.schmizz.sshj.sftp.RemoteFile file = SshjUtils.getInstance(context).getSFTPClient(uri).open(
                SshjUtils.getSftpPath(uri), EnumSet.of(OpenMode.READ, OpenMode.WRITE));
            return new RandomAccessMediaFile() {
                public long length() throws Exception {
                    return file.length();
                }
                public int read(long at, byte[] b, int off, int len) throws Exception {
                    return file.read(at, b, off, len);
                }
                public void write(long at, byte[] b, int off, int len) throws Exception {
                    file.write(at, b, off, len);
                }
                // SSHJ's synchronous write waits for SFTP acknowledgement. SFTP v3 has no portable fsync.
                public void flush() {}
                public void close() throws Exception {
                    file.close();
                }
            };
        }
        final ParcelFileDescriptor fd;
        if (scheme == null || "file".equals(scheme)) {
            fd = ParcelFileDescriptor.open(new java.io.File(uri.getPath()), ParcelFileDescriptor.MODE_READ_WRITE);
        } else if ("content".equals(scheme)) {
            fd = context.getContentResolver().openFileDescriptor(uri, "rw");
        } else
            throw new IOException("This source does not support in-place metadata editing");
        if (fd == null)
            throw new IOException("Cannot open source for editing");
        return new RandomAccessMediaFile() {
            public long length() throws Exception {
                return Os.fstat(fd.getFileDescriptor()).st_size;
            }
            public int read(long at, byte[] b, int off, int len) throws Exception {
                return Os.pread(fd.getFileDescriptor(), b, off, len, at);
            }
            public void write(long at, byte[] b, int off, int len) throws Exception {
                for (int done = 0; done < len;) {
                    int n = Os.pwrite(fd.getFileDescriptor(), b, off + done, len - done, at + done);
                    if (n <= 0)
                        throw new IOException("Incomplete metadata write");
                    done += n;
                }
            }
            public void flush() throws Exception {
                Os.fsync(fd.getFileDescriptor());
            }
            public void close() throws Exception {
                fd.close();
            }
        };
    }
}
