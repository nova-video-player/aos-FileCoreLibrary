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


package com.archos.filecorelibrary;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.FileChannel;


public class ArchosFileChannel {
     /* @Override
    * Returns the current file size, as an integer number of bytes.
    */
    static public long transfer(long position, long count, FileInputStream src, FileOutputStream dst) throws IOException {
        FileChannel srcChannel = src.getChannel();
        FileChannel dstChannel = dst.getChannel();
        if (!srcChannel.isOpen()) {
            throw new ClosedChannelException();
        }
        if (!dstChannel.isOpen()) {
            throw new ClosedChannelException();
        }

        if (position < 0 || count < 0) {
            throw new IllegalArgumentException("position=" + position + " count=" + count);
        }

        if (count == 0 || position >= srcChannel.size()) {
            return 0;
        }
        count = Math.min(count, srcChannel.size() - position);

        FileDescriptor inFd = src.getFD();
        FileDescriptor outFd = dst.getFD();
        long rc = 0;
        rc = native_sendfile_64(outFd, inFd, position, count);
        return rc;
    }

    private static native int native_sendfile_64(FileDescriptor out, FileDescriptor in, long position, long count);
}
