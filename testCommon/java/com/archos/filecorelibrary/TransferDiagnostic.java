// Copyright 2026 Courville Software
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
import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;
import com.archos.filecorelibrary.sftp.SFTPSession;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.archos.environment.ArchosUtils;
import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

/** Shared host/ART diagnostic. Never print CSV URLs, usernames, passwords or backend exceptions. */
final class TransferDiagnostic {
    private static final class CheckFailure extends IOException {
        CheckFailure(String message) { super(message); }
    }
    static final class Row {
        final Uri uri;
        final String user, password, sha256;
        final long expectedBytes;
        Row(String line) {
            String[] fields = line.split(",", -1);
            if (fields.length < 1 || fields.length > 5 || fields[0].trim().isEmpty())
                throw new IllegalArgumentException("Expected url,user,password[,bytes[,sha256]]");
            uri = Uri.parse(fields[0].trim());
            user = fields.length > 1 ? fields[1].trim() : "";
            password = fields.length > 2 ? fields[2].trim() : "";
            expectedBytes = fields.length > 3 && !fields[3].trim().isEmpty()
                    ? Long.parseLong(fields[3].trim()) : -1;
            sha256 = fields.length > 4 ? fields[4].trim().toLowerCase(Locale.ROOT) : "";
            if (expectedBytes < -1 || (!sha256.isEmpty() && !sha256.matches("[0-9a-f]{64}")))
                throw new IllegalArgumentException("Invalid expected length or SHA-256");
        }
    }

    static void verify(long bytes, long expected, String hash, String expectedHash) throws IOException {
        if (expected < 0) throw new CheckFailure("Supply expected bytes when Content-Length is unknown");
        if (bytes != expected) throw new CheckFailure("Byte count mismatch");
        if (!expectedHash.isEmpty() && !expectedHash.equals(hash)) throw new CheckFailure("SHA-256 mismatch");
    }

    static int number(Function<String, String> args, String key, int fallback, int min, int max) {
        String value = args.apply(key);
        int n = value == null ? fallback : Integer.parseInt(value);
        if (n < min || n > max) throw new IllegalArgumentException("Invalid " + key);
        return n;
    }

    static void run(String csv, Function<String, String> args) throws Exception {
        List<Row> rows = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(new FileReader(csv))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
                try { rows.add(new Row(line)); }
                catch (RuntimeException e) { throw new IllegalArgumentException("Invalid CSV row " + (rows.size() + 1)); }
            }
        }
        if (rows.isEmpty()) throw new AssertionError("CSV has no usable rows");
        int buffer = number(args, "speedtestUpstreamBufferBytes", 81920, 1, 4 * 1024 * 1024);
        int depth = number(args, "speedtestSftpDepth", 0, 0, 64);
        int request = number(args, "speedtestRequestBytes", 0, 0, 1024 * 1024);
        int repeats = number(args, "speedtestRepeats", 1, 1, 100);
        int stress = number(args, "speedtestStressIterations", 0, 0, 1000);
        int sample = number(args, "speedtestSampleBytes", 0, 0, 256 * 1024 * 1024);
        String hint = args.apply("speedtestSmbjAccess");
        ReadOptions tuning = new ReadOptions(ReadOptions.Purpose.PLAYBACK, -1, false, depth, request,
                hint == null ? ReadOptions.SmbjAccess.RANDOM
                        : ReadOptions.SmbjAccess.valueOf(hint.toUpperCase(Locale.ROOT)));
        System.out.printf(Locale.US, "buffer=%d sftpDepth=%d requestCap=%d smbjAccess=%s repeats=%d stress=%d sampleBytes=%d%n",
                buffer, depth, request, tuning.smbjAccess, repeats, stress, sample);
        int failures = 0;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (!row.user.isEmpty()) NetworkCredentialsDatabase.getInstance().addCredential(
                    new NetworkCredentialsDatabase.Credential(row.user, row.password,
                            row.uri.buildUpon().path("").query(null).fragment(null).build().toString(), "", true));
            for (int repeat = 0; repeat < repeats; repeat++) {
                try { transfer(row, buffer, tuning, stress, sample, i + 1, repeat + 1); }
                catch (Exception | AssertionError e) {
                    failures++;
                    System.out.printf("row=%d run=%d FAILED (%s)%n", i + 1, repeat + 1, e instanceof CheckFailure ? e.getMessage() : e.getClass().getSimpleName());
                }
            }
        }
        if (failures != 0) throw new AssertionError(failures + " transfer/stress runs failed");
    }

    private static HttpURLConnection connect(StreamOverHttp proxy, Uri remote, String range) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(proxy.getEncodedUri(FileUtils.getName(remote)).toString()).openConnection();
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        if (range != null) conn.setRequestProperty("Range", range);
        return conn;
    }

    private static void transfer(Row row, int buffer, ReadOptions tuning, int stress, int sample, int index, int repeat) throws Exception {
        StreamDiagnostics metrics = new StreamDiagnostics();
        StreamOverHttp proxy = new StreamOverHttp(row.uri, "application/octet-stream", buffer, tuning, metrics);
        HttpURLConnection conn = null;
        long total = 0, fileLength = -1;
        String actualHash = "";
        boolean partialSample = false;
        long start = System.nanoTime();
        try {
            conn = connect(proxy, row.uri, null);
            if (conn.getResponseCode() != 200) throw new CheckFailure("Expected HTTP 200");
            long advertised = conn.getContentLengthLong();
            fileLength = row.expectedBytes >= 0 ? row.expectedBytes : advertised;
            if (fileLength < 0) throw new CheckFailure("Supply expected bytes when Content-Length is unknown");
            if (advertised >= 0 && advertised != fileLength) throw new CheckFailure("HTTP length mismatch");
            partialSample = sample > 0 && sample < fileLength;
            if (partialSample && !row.sha256.isEmpty())
                throw new CheckFailure("Full-file SHA-256 requires a full transfer");
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = conn.getInputStream()) {
                byte[] bytes = new byte[256 * 1024];
                while (sample == 0 || total < sample) {
                    int n = in.read(bytes, 0, sample == 0 ? bytes.length : (int) Math.min(bytes.length, sample - total));
                    if (n < 0) break;
                    if (n == 0) throw new CheckFailure("No progress");
                    total += n;
                    digest.update(bytes, 0, n);
                }
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : digest.digest()) hash.append(String.format(Locale.ROOT, "%02x", b & 255));
            actualHash = hash.toString();
            verify(total, partialSample ? sample : fileLength, actualHash, row.sha256);
        } finally {
            if (conn != null) conn.disconnect();
            proxy.close();
            checkCleanup(proxy, metrics);
        }
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf(Locale.US, "row=%d run=%d scheme=%s kind=%s bytes=%d fileBytes=%d seconds=%.3f MiB/s=%.3f sha256=%s %s%n",
                index, repeat, row.uri.getScheme(), partialSample ? "prefix" : "full", total, fileLength,
                seconds, total / 1048576.0 / seconds, actualHash, metrics);
        if (stress > 0) stress(row, fileLength, buffer, tuning, stress);
    }

    private static void checkCleanup(StreamOverHttp proxy, StreamDiagnostics metrics) throws Exception {
        if (!proxy.awaitIdle(35000)) throw new CheckFailure("Backend cleanup timed out");
        if (metrics.opened.get() != metrics.closed.get() || metrics.closeFailures.get() != 0)
            throw new CheckFailure("Backend ownership/close failure");
    }

    private static void stress(Row row, long length, int buffer, ReadOptions tuning, int iterations) throws Exception {
        if (length == 0) throw new CheckFailure("Stress test requires a nonempty file");
        ExecutorService metadata = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "Concurrent metadata diagnostic"); t.setDaemon(true); return t;
        });
        try {
            for (int i = 0; i < iterations; i++) {
                long offset = i % 2 == 0 ? 0 : Math.max(0, length - 65536);
                int count = (int) Math.min(65536, length - offset);
                Future<?> listing = metadata.submit(() -> {
                    try { RawListerFactory.getRawListerForUrl(FileUtils.getParentUrl(row.uri)).getFileList(); }
                    catch (Exception e) { throw new RuntimeException("Concurrent listing failed"); }
                });
                StreamDiagnostics metrics = new StreamDiagnostics();
                StreamOverHttp proxy = new StreamOverHttp(row.uri, "application/octet-stream", buffer, tuning, metrics);
                HttpURLConnection range = null, abandoned = null;
                try {
                    range = connect(proxy, row.uri, "bytes=" + offset + "-" + (offset + count - 1));
                    if (range.getResponseCode() != 206 || range.getContentLengthLong() != count)
                        throw new CheckFailure("Range status/length mismatch");
                    byte[] received;
                    try (InputStream in = range.getInputStream()) { received = readExactly(in, count); }
                    FileEditor editor = FileEditorFactory.getFileEditorForUrl(row.uri, ArchosUtils.getGlobalContext());
                    try (InputStream direct = editor.getInputStream(offset,
                            new ReadOptions(ReadOptions.Purpose.METADATA, count, true))) {
                        if (!Arrays.equals(received, readExactly(direct, count)))
                            throw new CheckFailure("Range payload mismatch");
                    }
                    abandoned = connect(proxy, row.uri, "bytes=" + offset + "-");
                    try (InputStream in = abandoned.getInputStream()) {
                        if (in.read() < 0) throw new CheckFailure("Empty playback stream");
                        // Supersede a response that the client has only started reading.
                        HttpURLConnection replacement = connect(proxy, row.uri,
                                "bytes=" + offset + "-" + (offset + count - 1));
                        try {
                            if (replacement.getResponseCode() != 206)
                                throw new CheckFailure("Replacement range failed");
                            try (InputStream next = replacement.getInputStream()) {
                                if (next.read() < 0) throw new CheckFailure("Empty replacement range");
                                proxy.close(); // Stop while responses/backend read-ahead can still be active.
                            }
                        } finally { replacement.disconnect(); }
                    }
                } finally {
                    if (range != null) range.disconnect();
                    if (abandoned != null) abandoned.disconnect();
                    proxy.close();
                    checkCleanup(proxy, metrics);
                }
                listing.get(35, TimeUnit.SECONDS);
                if ("sftp".equals(row.uri.getScheme())) SFTPSession.getInstance().removeSession(row.uri);
                if ("sshj".equals(row.uri.getScheme())) SshjUtils.disconnectSshClient(row.uri);
                System.out.printf("stress iteration=%d %s%n", i + 1, metrics);
            }
        } finally { metadata.shutdownNow(); }
    }

    private static byte[] readExactly(InputStream input, int count) throws IOException {
        byte[] bytes = new byte[count];
        int offset = 0;
        while (offset < count) {
            int n = input.read(bytes, offset, count - offset);
            if (n <= 0) throw new EOFException("Short range");
            offset += n;
        }
        if (input.read() != -1) throw new CheckFailure("Range exceeded bound");
        return bytes;
    }
}
