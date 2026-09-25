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
import static org.junit.Assert.*;
import java.io.*;
import org.junit.Test;

public class ReadOptionsTest {
    @Test public void boundAppliesBeforeBackendAndSurvivesShortReadsAndSkip() throws Exception {
        int[] calls = new int[1];
        InputStream source = new ByteArrayInputStream(new byte[100]) {
            @Override public synchronized int read(byte[] b, int off, int len) {
                calls[0]++;
                assertTrue(len <= 7);
                return super.read(b, off, Math.min(3, len));
            }
        };
        try (InputStream in = new ReadOptions(ReadOptions.Purpose.METADATA, 7, true).wrap(source)) {
            assertEquals(3, in.read(new byte[100]));
            assertEquals(2, in.skip(2));
            assertEquals(2, in.read(new byte[100]));
            assertEquals(-1, in.read());
            assertEquals(0, in.skip(100));
            assertEquals(0, in.read(new byte[0]));
            assertEquals(2, calls[0]);
        }
    }
    @Test public void requestCapAllowsShortReadsWithoutChangingPlaybackDefaults() throws Exception {
        ReadOptions tuning = new ReadOptions(ReadOptions.Purpose.PLAYBACK, -1, false,
                8, 4, ReadOptions.SmbjAccess.RANDOM);
        try (InputStream in = tuning.wrap(new ByteArrayInputStream(new byte[9]))) {
            assertEquals(4, in.read(new byte[9]));
            assertEquals(4, in.read(new byte[9]));
            assertEquals(1, in.read(new byte[9]));
        }
        assertEquals(16, ReadOptions.DEFAULT.pipelineDepth());
        assertEquals(1, new ReadOptions(ReadOptions.Purpose.PLAYBACK, 16, true).pipelineDepth());
    }
}
