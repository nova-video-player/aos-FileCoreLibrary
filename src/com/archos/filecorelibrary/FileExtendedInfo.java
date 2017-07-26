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

import java.io.File;
import java.io.Serializable;

public class FileExtendedInfo implements Comparable<FileExtendedInfo>, Serializable {

    public enum FileType {
        Directory, File, Shortcut, ZipFile, ZipDirectory, SmbFile, SmbDir
    };

    private final File file;
    private final FileType fileType;
    private final String name;
    private final String mimeType;
    private final String path;

    /**
     * @param f the file
     * @param name file name without the extension
     * @param mimeType file mime type
     */
    public FileExtendedInfo(File f, FileType fileType, String name, String mimeType, String path) {
        file = f;
        this.fileType = fileType;
        this.name = name;
        this.mimeType = mimeType;
        this.path = path;
    }

    public FileExtendedInfo(File f, FileType fileType, String name, String mimeType) {
        file = f;
        this.fileType = fileType;
        this.name = name;
        this.mimeType = mimeType;
        this.path = f.getPath();
    }

    public File getFile() {
        return file;
    }

    /**
     * @return the file name without the extension
     */
    public String getName() {
        return name;
    }

    public String getMimeType() {
        return mimeType;
    }

    public FileType getFileType() {
        return fileType;
    }

    public String getPath() {
        return path;
    }

    /*
     * Getting this info in this file instead of doing a
     * .getFile().isDirectory() enables a faster display of the list
     */
    public boolean isDirectory() {
        return fileType == FileType.Directory;
    }

    /*
     * Getting this info in this file instead of doing a .getFile().isFile()
     * enables a faster display of the list
     */
    public boolean isFile() {
        return fileType == FileType.File;
    }

    public boolean isShortcut() {
        return fileType == FileType.Shortcut;
    }

    public boolean isZipFile() {
        return fileType == FileType.ZipFile;
    }

    public boolean isZipDirectory() {
        return fileType == FileType.ZipDirectory;
    }

    public int compareTo(FileExtendedInfo f) {
        int ret = name.toLowerCase().compareTo(f.getName().toLowerCase());
        if (ret == 0) {
            // Same filename so compare the name with the extension.
            String pathOther = f.getPath();
            int index = path.lastIndexOf(File.pathSeparatorChar) + 1;
            int indexOther = pathOther.lastIndexOf(File.pathSeparatorChar) + 1;
            ret = path.substring(index).toLowerCase()
                    .compareTo(pathOther.substring(indexOther).toLowerCase());
        }
        return ret;
    }
}
