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

package com.archos.filecorelibrary.samba;

import static org.junit.Assert.assertEquals;

import com.archos.filecorelibrary.samba.NetworkCredentialsDatabase.Credential;

import org.junit.Test;

public class NetworkCredentialsDatabaseCredentialTest {

    @Test
    public void webdavKeepsFullEmailAsUsername() {
        Credential cred = new Credential("marc@courville.org", "pass", "webdavs://ewebdav.pcloud.com:443/", "", true);
        assertEquals("marc@courville.org", cred.getUsername());
        assertEquals("", cred.getDomain());
    }

    @Test
    public void emailIsNotSplitForSmb() {
        Credential cred = new Credential("marc@courville.org", "pass", "smb://server/share", "", true);
        assertEquals("marc@courville.org", cred.getUsername());
        assertEquals("", cred.getDomain());
    }

    @Test
    public void backslashDomainIsSplitForSmb() {
        Credential cred = new Credential("WORKGROUP\\marc", "pass", "smbj://server/share", "", true);
        assertEquals("marc", cred.getUsername());
        assertEquals("WORKGROUP", cred.getDomain());
    }

    @Test
    public void backslashDomainIsSplitForAllProtocols() {
        Credential cred = new Credential("WORKGROUP\\marc", "pass", "webdavs://server:443/", "", true);
        assertEquals("marc", cred.getUsername());
        assertEquals("WORKGROUP", cred.getDomain());
    }

    @Test
    public void explicitDomainIsPreserved() {
        Credential cred = new Credential("marc", "pass", "smb://server/share", "WORKGROUP", true);
        assertEquals("marc", cred.getUsername());
        assertEquals("WORKGROUP", cred.getDomain());
    }

    @Test
    public void anonymousStaysUntouched() {
        Credential cred = new Credential("anonymous", "", "webdavs://server:443/", "", true);
        assertEquals("anonymous", cred.getUsername());
        assertEquals("", cred.getDomain());
    }
}
