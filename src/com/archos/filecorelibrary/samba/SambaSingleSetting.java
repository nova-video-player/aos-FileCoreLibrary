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

public class SambaSingleSetting{

	static final private String SLASH = "/";
	public static final int TYPE_SMB = 0;
	public static final int TYPE_FTP = 1;
	public static final int TYPE_SFTP = 2;
	protected int type; 
	protected String section=null;//section == server | server/share
	protected boolean ignore;
	protected String password=null;
	protected boolean showhiddenshares;
	protected String username=null;
	protected boolean isShare;
	
	public SambaSingleSetting(String section, boolean share){
		this.section=section;
		this.isShare = share;
		this.type=0;
	}

	public SambaSingleSetting(String server) {
		isShare = false;
		if (server.charAt(0) != '/')
			section = SLASH.concat(server);
		else
			section = server;
	}

	public SambaSingleSetting(String server, String share){
		if (server!=null && !server.isEmpty()) {
			if (server.charAt(0) != '/')
				section = SLASH.concat(server);
			else
				section = server;
		}

		if (section!=null) {
			if (share!=null && !share.isEmpty()) {
				isShare=true;
				if (share.charAt(0) != '/')
					section = section.concat(SLASH.concat(share));
				else
					section = section.concat(share);
			} else {
				isShare=false;
			}
		}
	}

	public String getSection(){
		return this.section;
	}

	public String getServer() {
		return section.split("/")[1];
	}

	public String getShare() {
		String share = null;
		if (isAShare() && section!=null) {
			String[] splitted = section.split("/");
			if (splitted!=null && splitted.length>=3) {
				share = splitted[2];
			}
		}
		return share;
	}

	public boolean isAShare(){
		return isShare;
	}

	public void setUsername(String domain, String login){
		String dom = domain;
		if (dom!=null && !dom.isEmpty()) {
		    if (dom.charAt(0) == '/')
			    dom = dom.substring(1);
		    if (!dom.endsWith("/"))
		        dom = dom.concat(SLASH);
		}

		this.username=dom.concat(login);
	}

	public void setUsername(String username){
		this.username=username;
	}

	public String getUsername(){
		return this.username;
	}

	public void setPassword(String password){
		this.password=password;
	}

	public String getPassword(){
		return this.password;
	}

	public void setHidden(boolean hidden){
		this.showhiddenshares= hidden;
	}

	public boolean isHiddenDisplayed(){
		return this.showhiddenshares;
	}

	public void setIgnore(boolean ignore){
		this.ignore=ignore;
	}

	public boolean isIgnored(){
		return this.ignore;
	}

    public int getType() {
        return this.type;
    }
    public void setType(int type) {
        this.type=type;
    }

    public void setType(String substring) {
        try{
            type=Integer.parseInt(substring);
        }
        catch(NumberFormatException e){}
        
    }

}
