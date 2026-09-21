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

package com.archos.filecorelibrary.ftp;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.SocketException;

import org.apache.commons.net.ftp.FTP;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.archos.filecorelibrary.AuthenticationException;
import com.archos.filecorelibrary.FileEditor;
import android.net.Uri;

public class FtpFileEditor extends FileEditor {
    private static final Logger log = LoggerFactory.getLogger(FtpFileEditor.class);

    public FtpFileEditor(Uri uri) {
        super(uri);
    }

    private FTPClient getClient() throws SocketException, IOException, AuthenticationException {
        if ("ftps".equals(mUri.getScheme())) {
            return Session.getInstance().getNewFTPSClient(mUri, FTP.BINARY_FILE_TYPE);
        } else {
            return Session.getInstance().getNewFTPClient(mUri, FTP.BINARY_FILE_TYPE);
        }
    }

    @Override
    public boolean touchFile() {
        return false;
    }

    @Override
    public boolean mkdir() {
        if (log.isDebugEnabled()) log.debug("mkdir: {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                return ftp.makeDirectory(mUri.getPath());
            }
        } catch (AuthenticationException e) {
            log.error("mkdir: Caught AuthenticationException: ", e);
        } catch (SocketException e) {
            log.error("mkdir: Caught SocketException: ", e);
        } catch (IOException e) {
            log.error("mkdir: Caught IOException: ", e);
        } finally {
            Session.close(ftp);
        }
        return false;
    }

    private InputStream wrapInputStream(final InputStream is, final FTPClient ftp) {
        return new InputStream() {
            private boolean mClosed = false;

            @Override
            public void close() throws IOException {
                if (mClosed) return;
                mClosed = true;
                try {
                    is.close();
                } catch (IOException e) {
                    log.warn("wrapInputStream: caught IOException closing data stream: {}", e.getMessage());
                } finally {
                    try {
                        ftp.completePendingCommand();
                    } catch (Exception e) {
                        log.warn("wrapInputStream: caught Exception completing pending command: {}", e.getMessage());
                    } finally {
                        Session.close(ftp);
                    }
                }
            }

            @Override
            public int read() throws IOException {
                return is.read();
            }

            @Override
            public int read(byte[] b) throws IOException {
                return is.read(b);
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                return is.read(b, off, len);
            }

            @Override
            public long skip(long n) throws IOException {
                return is.skip(n);
            }

            @Override
            public int available() throws IOException {
                return is.available();
            }

            @Override
            public void mark(int readlimit) {
                is.mark(readlimit);
            }

            @Override
            public void reset() throws IOException {
                is.reset();
            }

            @Override
            public boolean markSupported() {
                return is.markSupported();
            }
        };
    }

    private OutputStream wrapOutputStream(final OutputStream os, final FTPClient ftp) {
        return new OutputStream() {
            private boolean mClosed = false;

            @Override
            public void write(int b) throws IOException {
                os.write(b);
            }

            @Override
            public void write(byte[] b) throws IOException {
                os.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                os.write(b, off, len);
            }

            @Override
            public void flush() throws IOException {
                os.flush();
            }

            @Override
            public void close() throws IOException {
                if (mClosed) return;
                mClosed = true;
                try {
                    os.close();
                } catch (IOException e) {
                    log.warn("wrapOutputStream: caught IOException closing data stream: {}", e.getMessage());
                } finally {
                    try {
                        ftp.completePendingCommand();
                    } catch (Exception e) {
                        log.warn("wrapOutputStream: caught Exception completing pending command: {}", e.getMessage());
                    } finally {
                        Session.close(ftp);
                    }
                }
            }
        };
    }

    @Override
    public InputStream getInputStream() throws AuthenticationException, SocketException, IOException {
        if (log.isDebugEnabled()) log.debug("getInputStream: {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp == null) {
                throw new IOException("Failed to connect to " + mUri);
            }
            InputStream is = ftp.retrieveFileStream(mUri.getPath());
            if (is == null) {
                throw new IOException("Failed to retrieve file stream for " + mUri.getPath() + ", reply: " + ftp.getReplyString());
            }
            return wrapInputStream(is, ftp);
        } catch (Throwable t) {
            Session.close(ftp);
            if (t instanceof AuthenticationException) throw (AuthenticationException) t;
            if (t instanceof SocketException) throw (SocketException) t;
            if (t instanceof IOException) throw (IOException) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new IOException(t);
        }
    }

    @Override
    public InputStream getInputStream(long from) throws Exception {
        if (log.isDebugEnabled()) log.debug("getInputStream: {} from {}", mUri.getPath(), from);
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp == null) {
                throw new IOException("Failed to connect to " + mUri);
            }
            if (from > 0) {
                ftp.setRestartOffset(from);
            }
            InputStream is = ftp.retrieveFileStream(mUri.getPath());
            if (is == null) {
                throw new IOException("Failed to retrieve file stream at offset " + from + " for " + mUri.getPath() + ", reply: " + ftp.getReplyString());
            }
            return wrapInputStream(is, ftp);
        } catch (Throwable t) {
            Session.close(ftp);
            if (t instanceof Exception) throw (Exception) t;
            throw new IOException(t);
        }
    }

    @Override
    public OutputStream getOutputStream() throws AuthenticationException, SocketException, IOException {
        if (log.isDebugEnabled()) log.debug("getOutputStream: {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp == null) {
                throw new IOException("Failed to connect to " + mUri);
            }
            OutputStream os = ftp.storeFileStream(mUri.getPath());
            if (os == null) {
                throw new IOException("Failed to store file stream for " + mUri.getPath() + ", reply: " + ftp.getReplyString());
            }
            return wrapOutputStream(os, ftp);
        } catch (Throwable t) {
            Session.close(ftp);
            if (t instanceof AuthenticationException) throw (AuthenticationException) t;
            if (t instanceof SocketException) throw (SocketException) t;
            if (t instanceof IOException) throw (IOException) t;
            if (t instanceof RuntimeException) throw (RuntimeException) t;
            throw new IOException(t);
        }
    }

    @Override
    public Boolean delete() throws SocketException, IOException, AuthenticationException {
        if (log.isDebugEnabled()) log.debug("delete: {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                return ftp.deleteFile(mUri.getPath());
            }
        } finally {
            Session.close(ftp);
        }
        return false;
    }

    @Override
    public boolean rename(String newName) {
        if (log.isDebugEnabled()) log.debug("rename: {} to {}", mUri.getPath(), newName);
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                String parent = new File(mUri.getPath()).getParent();
                String targetPath = new File(parent, newName).getPath();
                return ftp.rename(mUri.getPath(), targetPath);
            }
        } catch (Exception e) {
            log.warn("rename: failed to rename {} to {}", mUri, newName, e);
        } finally {
            Session.close(ftp);
        }
        return false;
    }

    @Override
    public boolean move(Uri uri) {
        if (!mUri.getScheme().equals(uri.getScheme()) || !mUri.getHost().equals(uri.getHost()) || mUri.getPort() != uri.getPort())
            return false;
        if (log.isDebugEnabled()) log.debug("move: {} to {}", mUri, uri);
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                return ftp.rename(mUri.getPath(), uri.getPath());
            }
        } catch (Exception e) {
            log.warn("move: failed to move {} to {}", mUri, uri, e);
        } finally {
            Session.close(ftp);
        }
        return false;
    }

    @Override
    public boolean exists() {
        if (log.isTraceEnabled()) log.trace("exists: checking {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                return ftpFile != null;
            }
        } catch (Exception e) {
            log.warn("exists: failed to check existence for {}", mUri, e);
        } finally {
            Session.close(ftp);
        }
        return false;
    }

    @Override
    public long length() throws Exception {
        if (log.isTraceEnabled()) log.trace("length: checking {}", mUri.getPath());
        FTPClient ftp = null;
        try {
            ftp = getClient();
            if (ftp != null) {
                String sizeStr = ftp.getSize(mUri.getPath());
                if (sizeStr != null) {
                    try {
                        return Long.parseLong(sizeStr.trim());
                    } catch (NumberFormatException ignored) {}
                }
                FTPFile ftpFile = ftp.mlistFile(mUri.getPath());
                if (ftpFile != null) {
                    return ftpFile.getSize();
                }
            }
        } catch (Exception e) {
            log.warn("length: failed to get length for {}", mUri, e);
        } finally {
            Session.close(ftp);
        }
        return -1;
    }
}
