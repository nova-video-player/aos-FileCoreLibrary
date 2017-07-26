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

package com.archos.filecorelibrary.samba;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Base64;

import java.io.Serializable;
import java.security.Key;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * New credentials database for every kind of remote share
 * 
 * uri -> credential
 * @author alexandre
 *
 */     
public class NetworkCredentialsDatabase {
    private static NetworkCredentialsDatabase networkDatabase;
    //useful to keep both temporary and saved credentials
    private HashMap<String,Credential> mCredentials;
    private DatabaseHelper mDBHelper;
    private SQLiteDatabase mDB;
    private static final String DATABASE_NAME = "credentials_db";
    private static final String CREDENTIALS_TABLE = "credentials_table";
    private static final String KEY_PATH = "path";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_PASSWORD = "password";
    private static final String[] COLS = { KEY_PATH, KEY_USERNAME, KEY_PASSWORD };
    private static final byte[] cipherKey = "vimcufJies8".getBytes();

    private static final String DATABASE_CREATE_CREDENTIALS =
            "create table "+CREDENTIALS_TABLE+" (" + KEY_PATH + " text not null primary key, "+KEY_USERNAME+" text, " + KEY_PASSWORD + " text);";
    public static final int DATABASE_VERSION = 1;

    public static class Credential implements Serializable{
        String mUsername;
        String mPassword;
        String mUriString;
        boolean mIsTemporary;
        public Credential(String username, String password, String uriString, boolean isTemporary){
            mUsername = username;
            mPassword = password;
            mUriString = uriString;
            mIsTemporary = isTemporary;
        }
        public String getUriString(){
            return mUriString;
        }
        public String getPassword(){
            return mPassword;
        }
        public String getUsername(){
            return mUsername;
        }
        public void setPassword(String password){mPassword = password;}
        public void setUsername(String username){mUsername = username;}
        public boolean isTemporary(){
            return mIsTemporary;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Credential that = (Credential) o;

            if (mPassword != null ? !mPassword.equals(that.mPassword) : that.mPassword != null)
                return false;
            if (mUriString != null ? !mUriString.equals(that.mUriString) : that.mUriString != null)
                return false;
            if (mUsername != null ? !mUsername.equals(that.mUsername) : that.mUsername != null)
                return false;

            return true;
        }

        @Override
        public int hashCode() {
            int result = mUsername != null ? mUsername.hashCode() : 0;
            result = 31 * result + (mPassword != null ? mPassword.hashCode() : 0);
            result = 31 * result + (mUriString != null ? mUriString.hashCode() : 0);
            return result;
        }
    }


    public NetworkCredentialsDatabase(){
        mCredentials = new HashMap<String, NetworkCredentialsDatabase.Credential>();
    }
    /**
     * Will add a credential to temporary credential list, 
     * THIS WON'T SAVE IT use saveCredential instead
     */
    public void addCredential(Credential cred){
        mCredentials.put(cred.getUriString(), cred);
    }

    public void saveCredential(Credential cred){
        mCredentials.put(cred.getUriString(), cred);
        open();
        ContentValues initialValues = new ContentValues(1);
        initialValues.put(KEY_PATH, cred.getUriString());
        initialValues.put(KEY_USERNAME, cred.getUsername());
        initialValues.put(KEY_PASSWORD, encrypt(cred.getPassword()));
        mDB.insertWithOnConflict(CREDENTIALS_TABLE, null, initialValues, SQLiteDatabase.CONFLICT_REPLACE);
        cred.mIsTemporary = false;
        close();
    }
    public List<Credential> getAllPersistentCredentials(){
        List<Credential> persistentCredentials =  new ArrayList<Credential>();
        for(Credential cred : mCredentials.values()){
            if(!cred.isTemporary())
                persistentCredentials.add(cred);
        }
        return persistentCredentials;
    }
    public void loadCredentials(Context ct){
        if(mCredentials.size()==0) {
            mDBHelper = new DatabaseHelper(ct);
            try {
                open();
                Cursor cursor = mDB.query(CREDENTIALS_TABLE,
                        COLS,
                        null,
                        null,
                        null,
                        null,
                        null);
                if (cursor != null) {
                    int pathColumnIndex = cursor.getColumnIndex(KEY_PATH);
                    int usernameColumnIndex = cursor.getColumnIndex(KEY_USERNAME);
                    int passwordColumnIndex = cursor.getColumnIndex(KEY_PASSWORD);
                    int shortcutCount = cursor.getCount();

                    if (shortcutCount > 0) {
                        cursor.moveToFirst();
                        do {
                            String path = cursor.getString(pathColumnIndex);
                            String username = cursor.getString(usernameColumnIndex);
                            String password = decrypt(cursor.getString(passwordColumnIndex));
                            mCredentials.put(path, new Credential(username, password, path, false));
                        } while (cursor.moveToNext());
                    }
                cursor.close();
                }
                close();
                //load old credentials database
                for (String str : SambaConfiguration.getSingleSettingList()) {
                    SambaSingleSetting single = SambaConfiguration.getSingleSetting(str);
                    String username = single.getUsername();
                    if (username.lastIndexOf("/") != -1) {
                        username = username.substring(username.lastIndexOf("/") + 1);
                    }
                    String password = single.getPassword();
                    String path = "smb://" + single.getServer() + (single.getShare() != null ? "/" + single.getShare() : "");
                    saveCredential(new Credential(username, password, path, false));
                    SambaConfiguration.deleteSingleSetting(single.getSection());

                }
            }
            catch (SQLException e) { // to avoid lockexception, we still don't know what is causing it
                e.printStackTrace();
            }
        }
    }
    private void open() throws SQLException {
        mDB = mDBHelper.getWritableDatabase();
    }

    /**
     * Closes database
     */
    private void close() {
        if (mDB != null) {
            mDB.close();
        }
        if (mDBHelper != null) {
            mDBHelper.close();
        }
    }
    public static NetworkCredentialsDatabase getInstance(){
        if(networkDatabase==null){
            networkDatabase = new NetworkCredentialsDatabase();
        }
        return networkDatabase;

    }
    public void deleteCredential(String uriString){
        open();
        String [] args = new String[1];
        args[0] = uriString;
        mDB.delete(CREDENTIALS_TABLE, KEY_PATH+"= ?",args);
        close();
        mCredentials.remove(uriString);
    }
    public Credential getCredential(String uriString){
        if(mCredentials.containsKey(uriString)) {
            return mCredentials.get(uriString);

        }
        if(uriString.endsWith("/")&&uriString.length()>1&&mCredentials.containsKey(uriString.substring(0, uriString.length()-1))){
            return mCredentials.get(uriString.substring(0, uriString.length()-1));
        }
        /*
            now we check if a parent has credentials, we will keep the longest one
         */
        Credential ret = null;
        for(String parent : mCredentials.keySet()){
            if(uriString.startsWith(parent)){ // this is potentially the right one
                if(parent.endsWith("/")&&(ret==null||ret.getUriString().length()<parent.length())) { // this one is appropriate
                    ret = mCredentials.get(parent);
                }
                else{
                    //we have to check if the caracter after the string "parent" is a / or nothing
                    // to avoid these cases :
                    // credential for /this/is/a/path
                    // uriString : /this/is/a/pathbutdifferent
                    if(uriString.charAt(parent.length())=='/'&&(ret==null||ret.getUriString().length()<parent.length())) {
                        ret = mCredentials.get(parent);
                    }
                }
            }
        }

        return ret;

    }

    private static String encrypt(String password){
        try {
            Key key = new SecretKeySpec(cipherKey,"Blowfish");
            Cipher cipher=Cipher.getInstance("Blowfish");
            cipher.init(Cipher.ENCRYPT_MODE,key);
            return Base64.encodeToString(cipher.doFinal(password.getBytes()), Base64.DEFAULT);
        }
        catch (Exception e) {
            return null;
        }
    }

    private static String decrypt(String password){
        try {
            Key key = new SecretKeySpec(cipherKey,"Blowfish");
            Cipher cipher=Cipher.getInstance("Blowfish");
            cipher.init(Cipher.DECRYPT_MODE,key);
            return new String(cipher.doFinal(Base64.decode(password.getBytes(), Base64.DEFAULT)));
        }
        catch (Exception e) {
            return null;
        }
    }

    private  class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(DATABASE_CREATE_CREDENTIALS);
        }
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {            
        }
    }
}
