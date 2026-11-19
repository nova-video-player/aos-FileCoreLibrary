// Copyright 2026 Courville Software
// SPDX-License-Identifier: Apache-2.0
package com.archos.filecorelibrary;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import android.content.Context;
import android.net.Uri;
import com.archos.filecorelibrary.smbj.SmbjUtils;
import com.archos.filecorelibrary.sshj.SshjUtils;
import com.hierynomus.msdtyp.AccessMask;
import com.hierynomus.mssmb2.*;
import com.hierynomus.smbj.share.DiskShare;
import java.util.EnumSet;
import net.schmizz.sshj.sftp.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockedStatic;
import org.robolectric.*;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28)
public class RandomAccessMediaFileTest {
    @Test public void smbOpensExistingFileAndCompletesPartialWrites() throws Exception {
        Context context=RuntimeEnvironment.getApplication();Uri uri=Uri.parse("smb://server/share/movie.mkv");
        SmbjUtils utils=mock(SmbjUtils.class);DiskShare share=mock(DiskShare.class);
        com.hierynomus.smbj.share.File file=mock(com.hierynomus.smbj.share.File.class);
        try(MockedStatic<NetworkInitialization> ready=mockStatic(NetworkInitialization.class);
            MockedStatic<SmbjUtils> singleton=mockStatic(SmbjUtils.class)) {
            singleton.when(() -> SmbjUtils.getInstance(context)).thenReturn(utils);
            when(utils.getSmbShare(uri)).thenReturn(share);
            when(share.openFile(anyString(),any(),isNull(),any(),any(),isNull())).thenReturn(file);
            byte[] bytes={4,5,6};
            when(file.write(bytes,20L,0,3)).thenReturn(1L);when(file.write(bytes,21L,1,2)).thenReturn(2L);
            try(RandomAccessMediaFile handle=RandomAccessMediaFile.open(context,uri)) {handle.write(20,bytes,0,3);handle.flush();}
            verify(share).openFile(eq(FileUtils.getFilePath(uri)),eq(EnumSet.of(AccessMask.GENERIC_READ,AccessMask.GENERIC_WRITE)),
                isNull(),eq(EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ)),eq(SMB2CreateDisposition.FILE_OPEN),isNull());
            verify(file).write(bytes,21L,1,2);verify(file).flush();verify(file).close();
        }
    }
    @Test public void sftpUsesOnlyReadAndWriteAndClosesHandle() throws Exception {
        Context context=RuntimeEnvironment.getApplication();Uri uri=Uri.parse("sftp://server/movie.mkv");
        SshjUtils utils=mock(SshjUtils.class);SFTPClient client=mock(SFTPClient.class);RemoteFile file=mock(RemoteFile.class);
        try(MockedStatic<NetworkInitialization> ready=mockStatic(NetworkInitialization.class);
            MockedStatic<SshjUtils> singleton=mockStatic(SshjUtils.class)) {
            singleton.when(() -> SshjUtils.getInstance(context)).thenReturn(utils);
            singleton.when(() -> SshjUtils.getSftpPath(uri)).thenReturn("/movie.mkv");
            when(utils.getSFTPClient(uri)).thenReturn(client);
            when(client.open("/movie.mkv",EnumSet.of(OpenMode.READ,OpenMode.WRITE))).thenReturn(file);
            byte[] bytes={1,2};
            try(RandomAccessMediaFile handle=RandomAccessMediaFile.open(context,uri)) {handle.write(42,bytes,0,2);}
            verify(client).open("/movie.mkv",EnumSet.of(OpenMode.READ,OpenMode.WRITE));
            verify(file).write(42,bytes,0,2);verify(file).close();
        }
    }
}
