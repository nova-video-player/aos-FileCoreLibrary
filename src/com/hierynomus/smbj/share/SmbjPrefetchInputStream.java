package com.hierynomus.smbj.share;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.mssmb2.messages.SMB2ReadResponse;
import com.hierynomus.protocol.commons.concurrent.Futures;
import com.hierynomus.protocol.transport.TransportException;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A prefetching InputStream for SMBJ that uses a sliding window of asynchronous reads.
 * This class is in the com.hierynomus.smbj.share package to access package-private File.readAsync().
 */
public final class SmbjPrefetchInputStream extends InputStream {
    private static final int MAX_PENDING_READS = 3;
    private final File file;
    private final int bufferSize;
    private final long readTimeout;
    private final Queue<Future<SMB2ReadResponse>> pendingReads = new LinkedList<>();
    private long offset;
    private byte[] currentBuffer;
    private int currentPos;
    private boolean eof = false;
    private boolean closed = false;

    public SmbjPrefetchInputStream(File file, long offset, int bufferSize, long readTimeout) {
        this.file = file;
        this.offset = offset;
        this.bufferSize = bufferSize;
        this.readTimeout = readTimeout;
        prefetch();
    }

    private void prefetch() {
        while (!eof && pendingReads.size() < MAX_PENDING_READS) {
            pendingReads.add(file.readAsync(offset, bufferSize));
            offset += bufferSize;
        }
    }

    private boolean fillBuffer() throws IOException {
        if (eof || closed) return false;
        if (currentBuffer != null && currentPos < currentBuffer.length) return true;

        while (true) {
            Future<SMB2ReadResponse> future = pendingReads.poll();
            if (future == null) {
                eof = true;
                return false;
            }

            prefetch(); // Keep the window full

            SMB2ReadResponse response;
            try {
                response = Futures.get(future, readTimeout, TimeUnit.MILLISECONDS, TransportException.Wrapper);
            } catch (TransportException e) {
                throw new IOException(e);
            }

            long status = response.getHeader().getStatusCode();
            if (status == NtStatus.STATUS_END_OF_FILE.getValue()) {
                eof = true;
                return false;
            }
            if (status != NtStatus.STATUS_SUCCESS.getValue()) {
                throw new SMBApiException(response.getHeader(), "Read failed at offset " + (offset - (pendingReads.size() + 1) * (long)bufferSize));
            }

            byte[] data = response.getData();
            if (data == null || data.length == 0) {
                // Some servers return success with 0 bytes at EOF
                eof = true;
                return false;
            }

            currentBuffer = data;
            currentPos = 0;
            return true;
        }
    }

    @Override
    public int read() throws IOException {
        if (!fillBuffer()) return -1;
        return currentBuffer[currentPos++] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (b == null) throw new NullPointerException();
        if (off < 0 || len < 0 || len > b.length - off) throw new IndexOutOfBoundsException();
        if (len == 0) return 0;

        if (!fillBuffer()) return -1;
        int bytesToCopy = Math.min(len, currentBuffer.length - currentPos);
        System.arraycopy(currentBuffer, currentPos, b, off, bytesToCopy);
        currentPos += bytesToCopy;
        return bytesToCopy;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        // Cancel pending reads
        for (Future<SMB2ReadResponse> future : pendingReads) {
            future.cancel(true);
        }
        pendingReads.clear();
    }

    @Override
    public int available() {
        if (currentBuffer != null) return currentBuffer.length - currentPos;
        return 0;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) return 0;
        long skipped = 0;
        while (skipped < n) {
            if (!fillBuffer()) break;
            int canSkip = (int) Math.min(n - skipped, currentBuffer.length - currentPos);
            currentPos += canSkip;
            skipped += canSkip;
        }
        return skipped;
    }
}
