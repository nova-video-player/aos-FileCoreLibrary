package com.hierynomus.smbj.share;

import com.hierynomus.mserref.NtStatus;
import com.hierynomus.mssmb2.SMBApiException;
import com.hierynomus.mssmb2.messages.SMB2ReadResponse;
import com.hierynomus.protocol.commons.concurrent.Futures;
import com.hierynomus.protocol.transport.TransportException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * A simplified prefetching InputStream for SMBJ that uses a small sliding window (3 buffers).
 */
public final class SmbjPrefetchInputStream extends InputStream {
    private static final Logger log = LoggerFactory.getLogger(SmbjPrefetchInputStream.class);
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

    // Instrumentation
    private long totalBytesRead = 0;
    private long absoluteStartNs = 0;

    public SmbjPrefetchInputStream(File file, long offset, int bufferSize, long readTimeout) {
        this.file = file;
        this.offset = offset;
        this.bufferSize = bufferSize;
        this.readTimeout = readTimeout;
        
        if (log.isDebugEnabled()) {
            log.debug("SmbjPrefetchInputStream init: offset={}, bufferSize={}, window={}",
                    offset, bufferSize, MAX_PENDING_READS);
        }
        prefetch();
        absoluteStartNs = System.nanoTime();
    }

    private void prefetch() {
        while (!eof && pendingReads.size() < MAX_PENDING_READS) {
            pendingReads.add(file.readAsync(offset, bufferSize));
            offset += bufferSize;
        }
    }

    private boolean fillBuffer() throws IOException {
        if (closed) return false;
        if (currentBuffer != null && currentPos < currentBuffer.length) return true;
        if (eof) return false;

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
                continue; 
            }
            if (status != NtStatus.STATUS_SUCCESS.getValue()) {
                throw new SMBApiException(response.getHeader(), "Read failed at offset " + (offset - (pendingReads.size() + 1) * (long)bufferSize));
            }

            byte[] data = response.getData();
            if (data == null || data.length == 0) {
                eof = true;
                continue;
            }

            currentBuffer = data;
            currentPos = 0;
            totalBytesRead += data.length;
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

        int totalBytesCopied = 0;
        while (totalBytesCopied < len) {
            if (!fillBuffer()) {
                return totalBytesCopied > 0 ? totalBytesCopied : -1;
            }
            int bytesToCopy = Math.min(len - totalBytesCopied, currentBuffer.length - currentPos);
            System.arraycopy(currentBuffer, currentPos, b, off + totalBytesCopied, bytesToCopy);
            currentPos += bytesToCopy;
            totalBytesCopied += bytesToCopy;

            if (currentPos < currentBuffer.length) break;
        }
        return totalBytesCopied;
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

    @Override
    public void close() throws IOException {
        if (!closed && log.isDebugEnabled()) {
            double totalSeconds = (System.nanoTime() - absoluteStartNs) / 1_000_000_000.0;
            double avgMbps = (totalBytesRead * 8.0) / (1024 * 1024 * Math.max(0.001, totalSeconds));
            log.debug("SmbjPrefetch close: total_read={} MB, total_time={} s, avg_throughput={} Mbps",
                    totalBytesRead / (1024 * 1024),
                    String.format("%.2f", totalSeconds),
                    String.format("%.2f", avgMbps));
        }
        closed = true;
        for (Future<SMB2ReadResponse> future : pendingReads) {
            future.cancel(true);
        }
        pendingReads.clear();
        file.close();
    }

    @Override
    public int available() throws IOException {
        if (currentBuffer == null || currentPos >= currentBuffer.length) {
            return 0;
        }
        return currentBuffer.length - currentPos;
    }
}
