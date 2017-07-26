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

import android.content.Context;
import android.net.Uri;

import com.archos.filecorelibrary.contentstorage.ContentProviderListingEngine;
import com.archos.filecorelibrary.ftp.FtpListingEngine;
import com.archos.filecorelibrary.jcifs.JcifListingEngine;
import com.archos.filecorelibrary.localstorage.LocalStorageListingEngine;
import com.archos.filecorelibrary.sftp.SFtpListingEngine;
import com.archos.filecorelibrary.zip.ZipListingEngine;

public class ListingEngineFactory {

    public static ListingEngine getListingEngineForUrl(Context context, Uri uri) {

        if (Utils.isLocal(uri)&&(uri.getScheme()==null||!uri.getScheme().equals("content"))) {
            return new LocalStorageListingEngine(context, uri);
        }else if(uri.getScheme().equals("content")){
            return new ContentProviderListingEngine(context, uri);
        }
        else if (uri.getScheme().equals("smb")) {
            return new JcifListingEngine(context, uri);
        }
        else if (uri.getScheme().equals("ftp")||uri.getScheme().equals("ftps")) {
            return new FtpListingEngine(context, uri);
        }
        else if (uri.getScheme().equals("sftp")) {
            return new SFtpListingEngine(context, uri);
        }
        else if (uri.getScheme().equals("zip")) {
            return new ZipListingEngine(context, uri);
        }
        else {
            throw new IllegalArgumentException("not implemented yet for "+uri);
        }
    }
}
