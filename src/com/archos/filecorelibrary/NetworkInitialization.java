// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.filecorelibrary;

import android.content.Context;
import android.net.Uri;
import android.os.Looper;
import android.os.Process;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.Provider;
import java.security.Security;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import jcifs.internal.smb2.Smb2SigningDigest;
import jcifs.smb.SmbException;
import jcifs.util.Crypto;

/** Coordinates crypto publication and credentials without holding up application navigation. */
public final class NetworkInitialization {
    private static final Logger log = LoggerFactory.getLogger(NetworkInitialization.class);
    private static BackgroundInitialization initialization;

    private NetworkInitialization() { }

    private static synchronized BackgroundInitialization getInitialization(Context context) {
        if (initialization == null) {
            if (context == null) throw new IllegalStateException("Application context unavailable");
            Context application = context.getApplicationContext();
            initialization = new BackgroundInitialization(() -> {
                long start = System.nanoTime();
                try {
                    Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                    warmCrypto();
                    NetworkCredentialsDatabase.getInstance().loadCredentials(application);
                    log.info("Network initialization ready in {} ms", (System.nanoTime() - start) / 1000000);
                    return null;
                } catch (Exception e) {
                    log.error("Network initialization failed", e);
                    throw e;
                }
            }, command -> {
                Thread thread = new Thread(command, "network-initialization");
                thread.start();
            });
        }
        return initialization;
    }

    public static void start(Context context) {
        getInitialization(context).start();
    }

    /** Call only from I/O workers. Cancellation of a caller never cancels shared initialization. */
    public static void awaitReady(Context context) throws SmbException {
        BackgroundInitialization task = getInitialization(context);
        if (task.isReady()) return;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new SmbException("Network initialization must be awaited off the main thread");
        }
        try {
            task.await(30, TimeUnit.SECONDS);
        } catch (IOException e) {
            throw new SmbException(e.getMessage(), e);
        }
    }

    public static void awaitReadyForSmb(Uri uri, Context context) throws SmbException {
        if (uri != null && ("smb".equalsIgnoreCase(uri.getScheme())
                || "smbj".equalsIgnoreCase(uri.getScheme()))) {
            awaitReady(context);
        }
    }

    static void warmCrypto() throws Exception {
        // Let jcifs own the instance, then reuse it globally. Constructing a second BC provider
        // both wastes startup CPU and allows competing lazy initialization during SMB login.
        Provider provider = Crypto.getProvider();
        // Parse legacy registrations before publishing BC to concurrent JCA users (e.g. TLS).
        provider.getServices();
        for (String algorithm : new String[] { "MD4", "MD5", "SHA-512" }) {
            MessageDigest.getInstance(algorithm, provider).digest(new byte[0]);
        }
        for (String algorithm : new String[] { "RC4", "DES/ECB/NoPadding", "Blowfish" }) {
            Cipher cipher = Cipher.getInstance(algorithm, provider);
            String keyAlgorithm = algorithm.startsWith("DES") ? "DES" : algorithm;
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(new byte[8], keyAlgorithm));
            cipher.doFinal(new byte[8]);
        }
        for (String algorithm : new String[] { "HmacMD5", "HmacSHA256", "AESCMAC" }) {
            Mac mac = Mac.getInstance(algorithm, provider);
            mac.init(new SecretKeySpec(new byte[16], algorithm));
            mac.doFinal(new byte[0]);
        }
        if (Security.getProvider(provider.getName()) != provider) {
            Security.removeProvider(provider.getName());
            Security.insertProviderAt(provider, 1);
        }
        // Exercise jcifs' actual default-provider lookups and SMB 3 key derivation as well.
        Crypto.getMD4().digest();
        Crypto.getMD5().digest();
        Crypto.getSHA512().digest();
        Crypto.getHMACT64(new byte[16]).digest();
        Crypto.getArcfour(new byte[16]).doFinal(new byte[8]);
        Crypto.getDES(new byte[7]).doFinal(new byte[8]);
        new Smb2SigningDigest(new byte[16], 0x0210, null);
        new Smb2SigningDigest(new byte[16], 0x0311, new byte[64]);
    }
}
