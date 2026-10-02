// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0

package com.archos.filecorelibrary;

import static org.junit.Assert.*;

import java.security.Provider;
import java.security.Security;

import org.junit.Test;

import jcifs.util.Crypto;

public class NetworkCryptoTest {
    @Test
    public void warmupPublishesTheSameProviderUsedByJcifsAndCanBeRepeated() throws Exception {
        Provider original = Security.getProvider("BC");
        int position = 1;
        Provider[] providers = Security.getProviders();
        for (int i = 0; i < providers.length; i++) {
            if (providers[i] == original) position = i + 1;
        }
        try {
            NetworkInitialization.warmCrypto();
            Provider provider = Crypto.getProvider();
            assertSame(provider, Security.getProvider("BC"));
            assertSame(provider, Security.getProviders()[0]);
            assertEquals(64, Crypto.getSHA512().digest().length);
            NetworkInitialization.warmCrypto();
            assertSame(provider, Security.getProvider("BC"));
        } finally {
            Security.removeProvider("BC");
            if (original != null) Security.insertProviderAt(original, position);
        }
    }
}
