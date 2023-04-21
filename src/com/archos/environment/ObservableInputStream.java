// Copyright 2023 Courville Software
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

package com.archos.environment;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

public class ObservableInputStream extends InputStream implements Closeable {
    private final InputStream inputStream;
    private Runnable onClose;

    public ObservableInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    public void onClose(Runnable onClose) {
        this.onClose = onClose;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }

    @Override
    public void close() throws IOException {
        inputStream.close();
        if (onClose != null) {
            onClose.run();
        }
    }
}

