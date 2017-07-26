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

import android.os.Environment;
import android.util.Base64;

import com.archos.filecorelibrary.IOUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.util.LinkedList;
import java.util.StringTokenizer;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;


public class SambaConfiguration {

	protected static final String configFile = Environment.getExternalStorageDirectory()+"/Android/data/com.archos.mediacenter/smb/credentials.conf";
    protected static final String oldConfigFile = "/data/misc/smb/fusesmb.conf";
	static{
	    checkNewConfigFile();
	}
	private static final String GLOBAL = "[global]";
	private static final String NEWLINE = "\n";
	private static final String PASSWORD = "password=";
	static final String TAG = "SambaConfiguration";
	private static final String USERNAME = "username=";
	private static final String TYPE = "type=";
    private static final byte[] cipherKey = "vimcufJies8".getBytes();

    public static final String PREF_USEIP_KEY  = "use_ip";
    public static final boolean DEFAULT_USE_IP = false;

    // ------------------ Memory cached access --------------------------
    /**
     * get the list of existing server/shares configured - Cached
     */
    public static LinkedList<String> getSingleSettingList() {
        return SambaConfigurationCache.INSTANCE.getSingleSettingList();
    }
    /**
     * returns true if the section already exists in config file - Cached
     */
    public static boolean sectionExist(String section) {
        return SambaConfigurationCache.INSTANCE.sectionExist(section);
    }
    /**
     * get a single setting (for a share or a server) or null if not found - Cached
     */
    public static SambaSingleSetting getSingleSetting(String section) {
        return SambaConfigurationCache.INSTANCE.getSection(section);
    }

    // ------------------ Reading the FileSystem a lot ---------------
	/**
	 *get the list of existing server/shares configured
	 */
	public static LinkedList<String> getSingleSettingListUncached(){
		LinkedList<String> result = new LinkedList<String>();
		BufferedReader in = null;
		try{
		in = new BufferedReader(new FileReader(configFile));
		String current_line ;
		do{
			current_line=cleanLine(in.readLine());
			if (current_line !=null && current_line.startsWith("[/")) {
				result.add(current_line.substring(1,current_line.length() -1));

			}
		}
		while( current_line!=null);
		in.close();
		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		}
		return result;
	}

	/**
	 * return true if the section already exists in config file
	 */
	public static boolean sectionExistUncached(String section) {
	    BufferedReader in = null;
		try{
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null && !current_line.startsWith("["+ section + "]") );
			in.close();
			return current_line != null;
		}catch(java.io.FileNotFoundException e){
			e.printStackTrace();
		}
		catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		}
		return false;
	}

	/**
	 *set a single setting (for a server or a share)
	 *if section already exists, it will be replaced
	 *in that case, if no password or username is defined in new seetings, it will keep the old one
	 */
	public static void setSingleSetting(SambaSingleSetting setting){
		if (sectionExistUncached(setting.getSection())){
			replaceSingleSetting(setting);
		}else{
			addSingleSetting(setting);
		}
	}

	protected static void replaceSingleSetting(SambaSingleSetting setting){
	    BufferedReader in = null;
	    BufferedWriter out = null;
		try{
			SambaSingleSetting old_setting = getSingleSettingUncached(setting.getSection());
			if (null==old_setting){
				//error, setting not found
				return;
			}
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			StringBuilder stringBuilder = new StringBuilder();
			//go to the good section
			do{
				current_line=cleanLine(in.readLine());
				stringBuilder.append(current_line).append(NEWLINE);
			}
			while(current_line!=null && !current_line.startsWith("["+ setting.getSection() + "]") );
			//write new values
			if (null==setting.getUsername()){
				if (null!=old_setting.getUsername())
					stringBuilder.append(USERNAME).append(old_setting.getUsername()).append(NEWLINE);
			}else{
				stringBuilder.append(USERNAME).append(setting.getUsername()).append(NEWLINE);
			}
			if (null==setting.getPassword()){
				if (null!=old_setting.getPassword())
					stringBuilder.append(PASSWORD).append(encrypt(old_setting.getPassword())).append(NEWLINE);
			}else{
				stringBuilder.append(PASSWORD).append(encrypt(setting.getPassword())).append(NEWLINE);
			}
			if (-1!=setting.getType()){
                if (-1!=old_setting.getType())
                    stringBuilder.append(TYPE).append(old_setting.getType()).append(NEWLINE);
            }else{
                stringBuilder.append(TYPE).append(setting.getType()).append(NEWLINE);
            }
			if(!setting.isAShare()){
				stringBuilder.append("showhiddenshares=").append(String.valueOf(setting.isHiddenDisplayed()));
				stringBuilder.append("ignore=").append(String.valueOf(setting.isIgnored()));
			}
			//skip the old values
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '[' );

			//copy the rest of the file
			while(null!=current_line){
				stringBuilder.append(current_line).append(NEWLINE);
				current_line=cleanLine(in.readLine());
			}
			in.close();//end of reading , it s time to write
			out = new BufferedWriter(new FileWriter(configFile));
			out.write(stringBuilder.toString());
			out.close();

		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		    IOUtils.closeSilently(out);
		}
	}

	protected static void addSingleSetting(SambaSingleSetting setting){
		if (null== setting.getSection())
			return;
		BufferedWriter out = null;
		try{
			out = new BufferedWriter(new FileWriter(configFile,true));
			out.newLine();
			out.write("["+setting.getSection()+"]\n");
			if (null!=setting.getUsername())
				out.write(USERNAME + setting.getUsername() + NEWLINE);
			if (null!=setting.getPassword())
				out.write(PASSWORD + encrypt(setting.getPassword()) + NEWLINE);
			if (-1!=setting.getType())
			    out.write(TYPE + setting.getType() + NEWLINE);
			if (!setting.isAShare()){
				if(setting.isHiddenDisplayed())
					out.write("showhiddenshares=true\n" );
				else
					out.write("showhiddenshares=false\n" );
				if(setting.isIgnored())
					out.write("ignore=true\n");
				else
					out.write("ignore=false\n");
			}
			out.close();
		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(out);
		}
	}

	/**
	 *remove a section
	 */
	public static void deleteSingleSetting(String section){
	    BufferedReader in = null;
	    BufferedWriter out = null;
		try{
			StringBuilder stringBuilder = new StringBuilder();
			in = new BufferedReader(new FileReader(configFile));
			String current_line = cleanLine(in.readLine());
			final String sectionTemplate = "[" + section + "]";
			//go to the good section
			while(current_line!=null && !current_line.startsWith(sectionTemplate) ){
				stringBuilder.append(current_line).append(NEWLINE);
				current_line=cleanLine(in.readLine());
			}
			//skip the section to delete
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '[' );

			//copy the rest of the file
			while(null!=current_line){
				stringBuilder.append(current_line).append(NEWLINE);
				current_line=cleanLine(in.readLine());
			}
			in.close();//end of reading , it s time to write
			out = new BufferedWriter(new FileWriter(configFile));
			out.write(stringBuilder.toString());
			out.close();

		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		    IOUtils.closeSilently(out);
		}

	}

	/**
	 *get a single setting (for a share or a server) or null if not found
	 */
	public static SambaSingleSetting getSingleSettingUncached(String section) {

		SambaSingleSetting ret=null;
		int equalsign;
		BufferedReader in = null;
		try{
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			String value;
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null && !current_line.startsWith("["+ section + "]") );
			if (current_line==null){
				in.close();
				return null;//section not found
			}
			//section found
			boolean isShare;
			if (slashCount(section)<2)
				isShare=false;
			else
				isShare=true;

			ret= new SambaSingleSetting(section,isShare);
			current_line=cleanLine(in.readLine());

			while( current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '[' ){
				equalsign=current_line.indexOf('=');
				if (-1!=equalsign){
					value = current_line.substring(0,equalsign);

					if (value.equals("username")){
						ret.setUsername(current_line.substring(equalsign +1));
					}
					else if (value.equals("password")){
						ret.setPassword(decrypt(current_line.substring(equalsign +1)));
					}
					else if (value.equals("type")){
                        ret.setType(current_line.substring(equalsign +1));
                    }
					else if(!ret.isAShare()){//option for server only
						if (value.equals("ignore")){
							value = current_line.substring(equalsign +1);
							if (value.equals("true") || value.equals("1")){
								ret.setIgnore(true);
							}else if (value.equals("false") || value.equals("0")){
								ret.setIgnore(false);
							}

						}
						else if (value.equals("showhiddenshares")){
							value = current_line.substring(equalsign +1);
							if (value.equals("true") || value.equals("1")){
								ret.setHidden(true);
							}else if (value.equals("false") || value.equals("0")){
								ret.setHidden(false);
							}
						}

					} // else => error
				} // else => error unknown option, skip this line

				current_line=cleanLine(in.readLine());
			}
			//end of section

			in.close();
		}catch (java.lang.Exception e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		}
		return ret;

	}

	/**
	 *set the default password
	 */
	public static void setGlobalPassword(String password){
	    BufferedReader in = null;
	    BufferedWriter out = null;
		try{
			StringBuilder stringBuilder = new StringBuilder();
			boolean done=false;
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			//go to the good section
			do{
				current_line=cleanLine(in.readLine());
				stringBuilder.append(current_line).append(NEWLINE);
			}
			while(current_line!=null && !current_line.startsWith(GLOBAL) );
			if (current_line!=null){
				current_line=cleanLine(in.readLine());
				//copy section with change
				while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '[' && !done){
					if (current_line.startsWith("password")){
						stringBuilder.append(PASSWORD).append(encrypt(password)).append(NEWLINE);
						done=true;
					}else{
						stringBuilder.append(current_line).append(NEWLINE);
					}
					current_line=cleanLine(in.readLine());
				}
				if(!done)
					stringBuilder.append(PASSWORD).append(encrypt(password)).append(NEWLINE);

				//copy the rest of the file
				while(null!=current_line){
					stringBuilder.append(current_line).append(NEWLINE);
					current_line=cleanLine(in.readLine());
				}
			}else{
				stringBuilder.append(GLOBAL).append(NEWLINE).append(PASSWORD).append(encrypt(password)).append(NEWLINE);
			}
			in.close();//end of reading , it s time to write
			out = new BufferedWriter(new FileWriter(configFile));
			out.write(stringBuilder.toString());
			out.close();

		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		    IOUtils.closeSilently(out);
		}
	}

	/**
	 *set the default username
	 */
	public static void setGlobalUser(String user){
	    BufferedReader in = null;
	    BufferedWriter out = null;
		try{
			StringBuilder stringBuilder = new StringBuilder();
			boolean done=false;
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			//go to the good section
			do{
				current_line=cleanLine(in.readLine());
				stringBuilder.append(current_line).append(NEWLINE);
			}
			while(current_line!=null && !current_line.startsWith(GLOBAL) );
			if ( current_line!=null){
				current_line=cleanLine(in.readLine());
				//copy section with change
				while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '[' && !done){
					if (current_line.startsWith("username")){
						stringBuilder.append(USERNAME).append(user).append(NEWLINE);
						done=true;
					}else{
						stringBuilder.append(current_line).append(NEWLINE);
					}
					current_line=cleanLine(in.readLine());
				}
				if(!done)//no value to replace, add a new one
					stringBuilder.append(USERNAME).append(user).append(NEWLINE);

				//copy the rest of the file
				while(null!=current_line){
					stringBuilder.append(current_line).append(NEWLINE);
					current_line=cleanLine(in.readLine());
				}
			}else{//no global section, let s add one
				stringBuilder.append(GLOBAL).append(NEWLINE).append(USERNAME).append(user).append(NEWLINE);
			}

			in.close();//end of reading , it s time to write
			out = new BufferedWriter(new FileWriter(configFile));
			out.write(stringBuilder.toString());
			out.close();

		}catch(java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		    IOUtils.closeSilently(out);
		}
	}

	/**
	 *returns the default username or null if not found
	 */
	public static String getGlobalUser(){
		String ret = null;
		int equalsign;
		BufferedReader in = null;
		try{
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			String value;
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null &&  !current_line.startsWith(GLOBAL) );
			if (current_line==null){
				in.close();
				return null;//section not found
			}
			current_line=cleanLine(in.readLine());
			while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '['){
				equalsign=current_line.indexOf('=');

				if (-1!=equalsign){
					value = current_line.substring(0,equalsign);
					if (value.equals("username")){
						ret=current_line.substring(equalsign + 1);
					}
				}
				current_line=cleanLine(in.readLine());
			}
			in.close();

		}catch (java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		}
		return ret;
	}


	/**
	 *returns the default password or null if not found
	 */
	public static String getGlobalpassword(){
		String ret = null;
		int equalsign;
		BufferedReader in = null;
		try{
			in = new BufferedReader(new FileReader(configFile));
			String current_line ;
			String value;
			do{
				current_line=cleanLine(in.readLine());
			}
			while(current_line!=null && !current_line.startsWith(GLOBAL) );
			if (current_line==null){
				in.close();
				return null;//section not found
			}
			current_line=cleanLine(in.readLine());
			while(current_line!=null && !current_line.isEmpty() && current_line.charAt(0) != '['){
				equalsign = current_line.indexOf('=');

				if (-1!=equalsign){
					value = current_line.substring(0,equalsign);
					if (value.equals("password")){
						ret=decrypt(current_line.substring(equalsign + 1));
					}
				}
				current_line=cleanLine(in.readLine());
			}

			in.close();
		}catch (java.io.IOException e){
			e.printStackTrace();
		} finally {
		    IOUtils.closeSilently(in);
		}
		return ret;
	}

	protected static int slashCount(String section){
		int count = 0;
		for (int i =0; i< section.length();i++){
			if (section.charAt(i)=='/')
				count++;
		}
		return count;

	}
	protected static String cleanLine(String s) {
		if (null==s)return null;
		if(!s.isEmpty() && s.charAt(0) == '[')
            return s;
		int equalSign= s.indexOf('=');

		if(-1==equalSign){
			return removeSpaces(s);
		}else{
			return (removeSpaces(s.substring(0,equalSign+1)) + removeExternalSpaces(s.substring(equalSign+1)));
		}
	}

	protected static String removeExternalSpaces(String s){

		if(s.isEmpty() || (s.length()<2 && ' '==s.charAt(0)))
            return "";
        while(' '==s.charAt(0) && s.length()>1){
			s=s.substring(1);
		}
		while(' '==s.charAt(s.length()-1)){
			s=s.substring(0,s.length()-1);
		}
		return s;
	}

	protected static String removeSpaces(String s){
		StringTokenizer st = new StringTokenizer(s," \t",false);
		StringBuilder builder = new StringBuilder();
		while (st.hasMoreElements())
			builder.append(st.nextElement());
		return builder.toString();
	}

    public static void checkNewConfigFile(){
        File conf = new File(configFile);
        if (!conf.exists()){
            try {
                conf.getParentFile().mkdirs();
                File oldConf = new File(oldConfigFile);
                if(oldConf.exists())
                    copyFile(oldConf,conf);
                else
                    conf.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // copy a file from srcFile to destFile, return true if succeed, return
    // false if fail
    public static boolean copyFile(File srcFile, File destFile) {
        boolean result = false;
        try {
            InputStream in = new FileInputStream(srcFile);
            try {
                result = copyToFile(in, destFile);
            } finally  {
                in.close();
            }
        } catch (IOException e) {
            result = false;
        }
        return result;
    }

    public static boolean copyToFile(InputStream inputStream, File destFile) {
        FileOutputStream out = null;
        try {
            if (destFile.exists()) {
                destFile.delete();
            }
            out = new FileOutputStream(destFile);
            try {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) >= 0) {
                    out.write(buffer, 0, bytesRead);
                }
            } finally {
                out.flush();
                try {
                    out.getFD().sync();
                } catch (IOException e) {
                }
                out.close();
            }
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            IOUtils.closeSilently(out);
        }
    }

    private static String handleCredentials(String current){
        return handleCredentials(current, false);
    }

    private static String handleCredentials(String current, boolean force){
        String path = current;
        if (current.indexOf('@') != -1){
            if (force) {
                path = "smb://";
                path = path.concat(current.substring(current.indexOf('@') + 1));
            } else
                return current;
        }
        if(SambaConfiguration.sectionExist(path.substring(5, path.length()))){
            StringBuilder sb = new StringBuilder(path);
            SambaSingleSetting sss = SambaConfiguration.getSingleSetting(path.substring(5, path.length()));
            String username = sss.getUsername().substring(sss.getUsername().lastIndexOf('/')+1);
            if (sss.getPassword() == null)
                return current;
            sb.insert(6, username.concat(":").concat(sss.getPassword()).concat("@"));
            return sb.toString();
        } else {
            // No password or password not known yet
            return path;
        }
    }

    /** returns path without credentials */
    public static String getNoCredentialsPath(String src) {
        if (src == null || src.isEmpty() || !src.startsWith("smb://"))
            return src;
        int atSign = src.indexOf('@');
        if (atSign != -1)
            return "smb://" + src.substring(atSign + 1);
        return src;
    }

    public static String getCredentials(String smbPath){
        return getCredentials(smbPath, false);
    }

    public static String getCredentials(String smbPath, boolean forceCheck){
        if (!forceCheck && smbPath.indexOf('@') != -1){
            return smbPath;
        }
        LinkedList<String> credentialsList = getSingleSettingList();
        String noCreds = getNoCredentialsPath(smbPath);
        String path = noCreds;
        int position;
        while((position = path.lastIndexOf('/')) > 5){
            if (credentialsList.contains(path.substring(5, position+1))){
                String section = handleCredentials(path.substring(0, position+1));
                section = section.substring(0, section.length());
                return section+noCreds.substring(position+1);
            }
            path = path.substring(0, position);
        }
        return smbPath;
    }
    public static SambaSingleSetting getFTPCredentials(String server,int port){       
        String path = server.startsWith("/")?"":"/"+server+":"+port;
        return SambaConfigurationCache.INSTANCE.getSection(path);
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
}
