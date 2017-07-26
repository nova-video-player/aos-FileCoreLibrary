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

import java.util.Comparator;

import com.archos.filecorelibrary.ListingEngine.SortOrder;


public class FileComparator {

    public Comparator<MetaFile2> selectFileComparator(SortOrder order) {
        switch(order) {
            case SORT_BY_URI_ASC:
                return new SortByUriAsc();
            case SORT_BY_URI_DESC:
                return new SortByUriDesc();
            case SORT_BY_NAME_ASC:
                return new SortByNameAsc();
            case SORT_BY_NAME_DESC:
                return new SortByNameDesc();
            case SORT_BY_SIZE_ASC:
                return new SortBySizeAsc();
            case SORT_BY_SIZE_DESC:
                return new SortBySizeDesc();
            case SORT_BY_DATE_ASC:
                return new SortByDateAsc();
            case SORT_BY_DATE_DESC:
                return new SortByDateDesc();
        }
        return null;
    }

    public class SortByUriAsc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return file1.getUri().toString().compareToIgnoreCase(file2.getUri().toString());
        }
    }

    public class SortByUriDesc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return file2.getUri().toString().compareToIgnoreCase(file1.getUri().toString());
        }
    }

    public class SortByNameAsc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return file1.getName().compareToIgnoreCase(file2.getName());
        }
    }

    public class SortByNameDesc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return file2.getName().compareToIgnoreCase(file1.getName());
        }
    }

    public class SortBySizeAsc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            if (file1.isDirectory()) {
                // fallback : make no sense to sort directories by size
                return file1.getUri().toString().compareToIgnoreCase(file2.getUri().toString());
            } else {
                return Long.valueOf(file1.length()).compareTo(Long.valueOf(file2.length()));
            }
        }
    }

    public class SortBySizeDesc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            if (file1.isDirectory()) {
                // fallback : make no sense to sort directories by size
                return file2.getUri().toString().compareToIgnoreCase(file1.getUri().toString());
            } else {
                return Long.valueOf(file2.length()).compareTo(Long.valueOf(file1.length()));
            }
        }
    }

    public class SortByDateAsc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return Long.valueOf(file1.lastModified()).compareTo(Long.valueOf(file2.lastModified()));
        }
    }

    public class SortByDateDesc implements Comparator<MetaFile2> {
        @Override
        public int compare(MetaFile2 file1, MetaFile2 file2) {
            return Long.valueOf(file2.lastModified()).compareTo(Long.valueOf(file1.lastModified()));
        }
    }

}
