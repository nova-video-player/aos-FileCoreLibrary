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

import android.net.Uri;
import android.util.Log;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.contentstorage.ContentStorageFileEditor;
import com.archos.filecorelibrary.ftp.AuthenticationException;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import jcifs.util.transport.TransportException;

/**
 * This is simple HTTP local server for streaming InputStream to apps which are capable to read data from url.
 * Random access input stream is optionally supported, depending if file can be opened in this mode. 
 */
public class StreamOverHttp{
	private static final boolean debug = false;
	private static final String TAG = "StreamOverHttp";
	private final Uri mUri;
	private final String mName;

	private String fileMimeType;
	private static final int BUFFER_SIZE = 8192;
	private ServerSocket serverSocket;
	private Thread mainThread;
	private MetaFile2 mMetaFile;

	/**
	 * Some HTTP response status codes
	 */
	private static final String 
	HTTP_BADREQUEST = "400 Bad Request",
	HTTP_416 = "416 Range not satisfiable",
	HTTP_INTERNALERROR = "500 Internal Server Error";
	private ArrayList<MetaFile2> mSubList;
	private Uri mPosterLocalUri;
	private String mSubfolder;
	private int mPosterGenericResource;


	public StreamOverHttp(MetaFile2 f, String forceMimeType) throws IOException{
		mMetaFile = f;
		mUri= f.getUri();
		mName = f.getName();
		fileMimeType = forceMimeType!=null ? forceMimeType : "*/*";
        serverSocket = new ServerSocket(0);
		mainThread = new Thread(new Runnable(){
			public void run(){
				try{
					while(true) {
						Socket accept = serverSocket.accept();
						new HttpSession(accept,fileMimeType);
					}
				}catch(IOException e){
					if (debug)
						Log.w(TAG, e);
				}
			}

		});
		mainThread.setName("Stream over HTTP");
		mainThread.setDaemon(true);
		mainThread.start();
	}
    public StreamOverHttp(final Uri uri, final String forceMimeType) throws IOException{
		mUri = uri;
		mName = FileUtils.getName(mUri);
        fileMimeType = forceMimeType!=null ? forceMimeType : "*/*";
        serverSocket = new ServerSocket(0);
        mainThread = new Thread(new Runnable(){
            public void run(){

                try{
                    while(true) {
                        Socket accept = serverSocket.accept();
                        new HttpSession(accept,fileMimeType);
                    }
                }catch(IOException e){

                    if (debug)
                        Log.w(TAG, e);
                }
            }

        });
        mainThread.setName("Stream over HTTP");
        mainThread.setDaemon(true);
        mainThread.start();


    }
	private static final String[] SUBTITLES_ARRAY = { "idx", "smi", "ssa", "ass", "srr", "srt", "sub", "mpl", "txt","xml"};
	public List<MetaFile2> getSubtitleList(Uri video) throws SftpException, AuthenticationException, JSchException, IOException {
		if(mSubList!=null)
			return mSubList;
		final Uri parentUri = FileUtils.getParentUrl(video);
		final String videoFileName = video.getLastPathSegment();
		final String videoExtension = MimeUtils.getExtension(videoFileName);
		String filenameWithoutExtension;
		if (videoExtension!=null) { // may happen in UPnP
			filenameWithoutExtension = videoFileName.substring(0, videoFileName.length() - (videoExtension.length() + 1));
		} else {
			filenameWithoutExtension = videoFileName;
		}
		List<MetaFile2> metaFile2List =null;
		if(parentUri!=null&&!"upnp".equals(parentUri.getScheme())&&!"https".equals(parentUri.getScheme())&&!"http".equals(parentUri.getScheme()))
			metaFile2List = RawListerFactory.getRawListerForUrl(parentUri).getFileList();
		if(mSubfolder!=null){ //loading local subs
			if(metaFile2List==null)
				metaFile2List = new ArrayList<>();
			List<MetaFile2> metaFile2List2 = RawListerFactory.getRawListerForUrl(Uri.parse(mSubfolder)).getFileList();
			if(metaFile2List2!=null)
						metaFile2List.addAll(metaFile2List2);

		}
		List<String> subtitlesExtensions =Arrays.asList(SUBTITLES_ARRAY);
		String name;
		String extension;
		ArrayList<MetaFile2> subs = new ArrayList<>();
		if(metaFile2List!=null)
			for (MetaFile2 item : metaFile2List){
				name = item.getName();
				if (!name.startsWith(filenameWithoutExtension) || name.lastIndexOf('.') == -1)
					continue;
				extension = item.getExtension();
				if (subtitlesExtensions.contains(extension)){
					subs.add(item);
				}
			}
		mSubList = subs;
		return subs;
	}


	public void setLocalSubFolder(String subFolder) {
		mSubfolder = subFolder;
	}
	public int doesCurrentFileExists() {
		return mMetaFile!=null?1:0;
	}

	public Uri setPosterUri(Uri posterLocalUri, int posterGenericResource) {
		mPosterLocalUri = posterLocalUri;
		mPosterGenericResource = posterGenericResource;
		if(posterLocalUri==null) {
			mPosterLocalUri = Uri.parse("resource://"+"generic_poster.png");//special case when no poster
			return getUri("generic_poster.png");
		}
		return getUri(posterLocalUri.getLastPathSegment());
	}


	private class HttpSession implements Runnable{
		private boolean canSeek;
		private InputStream is;
		private final Socket socket;
		private Properties mPre=null;
		private int mRlen=-1;
		private InputStream mInS=null;
		private String fileMimeType =""; // this might be changed we a subtitle is sent
		private long length;

		HttpSession(Socket s, String fileMimeType){
			this.fileMimeType = fileMimeType;
			socket = s;
			if (debug) Log.i(TAG,"Stream over localhost: serving request on "+s.getInetAddress());
			Thread t = new Thread(this, "Http response");
			t.setDaemon(true);
			t.start();
		}

		public void run(){

			try{
				openInputStream();
				handleResponse(socket);
			}catch(IOException e){
				if (debug)
					Log.w(TAG, e);
			}finally {
				try {
					socket.close();
				} catch(Exception e) {}
				if(is!=null) {
					try{
						is.close();
					}catch(IOException e){
						if (debug)
							Log.w(TAG, e);
					}
				}
			}
		}

		private void openInputStream() throws IOException{

			boolean isAskingPoster = false;
			boolean needsToStream = false;
			long startFrom = 0;
			mPre = new Properties();
			mInS = socket.getInputStream();
			String path=null;
			if(mInS != null){
				byte[] buf = new byte[BUFFER_SIZE];
				mRlen = mInS.read(buf, 0, buf.length);
				ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, mRlen);
				BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
				try {
					String encodedPath;
					if((encodedPath=decodeHeader(socket, hin, mPre))!=null){
						String range = mPre.getProperty("range");
						if(range!=null){
							range = range.substring(6);

							int minus = range.indexOf('-');
							String startR = range.substring(0, minus);
							startFrom = Long.parseLong(startR);
							needsToStream = true;
						}
						path = Uri.decode(encodedPath);
					}
				} catch (InterruptedException e) { }
			}

			try {
				canSeek = true;

				/*
				 * first, we retrieve main metafile
				 */
				if(mMetaFile==null&&mUri!=null){
					try {
						mMetaFile = MetaFile2Factory.getMetaFileForUrl(mUri);
					}
					catch(Exception e){

					}

				}


				/*
					some players like MXPlayer try to find subs associated with http urls

				 */

				MetaFile2 metaFile2=null;
				MetaFile2 subFallback = null;
				/*
					Players such as mx player will look for subs having the exact same name as video file.
					But with AVP, when we download a sub file, its name is like *.eng.srt
					an easy hack is to send any sub with the asked extension when no sub with the exact same name has been found:
					if we have :
					name.srt
					name.eng.srt

					send name.srt

					if it ask for
					name.srt
					but we only have
					name.eng.srt
					send
					name.eng.srt
				 */
				if(path!=null){
					String name = FileUtils.getName(Uri.parse(path));
					if(mPosterLocalUri!=null&&name!=null&&name.equals(FileUtils.getName(mPosterLocalUri))){//if asking for poster
						isAskingPoster = true;
						if(!isResourcePoster(mPosterLocalUri))
							metaFile2 = MetaFile2Factory.getMetaFileForUrl(mPosterLocalUri);
					}else {
						if (!mName.equals(name)) {
							List<MetaFile2> subs = getSubtitleList(mUri);
							String extension = MimeUtils.getExtension(path);
							for (MetaFile2 sub : subs) {
								if (sub.getName().equals(name)) {
									metaFile2 = sub;

									break;
								}
								if (sub.getExtension().equals(extension)) {
									subFallback = sub;
								}
							}
							if (metaFile2 == null)
								metaFile2 = subFallback;
							if (metaFile2 != null) {

								canSeek = false;

							}
						}
					}
				}

				if(metaFile2==null&&!isAskingPoster)
					metaFile2 = mMetaFile;
				if(metaFile2!=null) { //mMetafile can be null
					if(metaFile2.length()!=0)
						length = metaFile2.length();
					try{
						is = FileEditorFactory.getFileEditorForUrl(mUri, ArchosUtils.getGlobalContext()).getInputStream(startFrom);
					}catch (IOException ioexception) {
						if (ioexception.getMessage().equals("Illegal seek")){
							is = FileEditorFactory.getFileEditorForUrl(mUri, ArchosUtils.getGlobalContext()).getInputStream();
							canSeek = false;
						}
					}
				}else {
					if (isAskingPoster && isResourcePoster(mPosterLocalUri)) {
						//special case, inputstream on resource
						is = ArchosUtils.getGlobalContext().getResources().openRawResource(mPosterGenericResource);
					} else {
						try {
							is = FileEditorFactory.getFileEditorForUrl(mUri, ArchosUtils.getGlobalContext()).getInputStream(startFrom);
						}catch (IOException ioexception) {
							if (ioexception.getMessage().equals("Illegal seek")){
								is = FileEditorFactory.getFileEditorForUrl(mUri, ArchosUtils.getGlobalContext()).getInputStream();
								canSeek = false;
							}
						}
					}
				}if(is==null)
					return;
				if(length==0)
					length = is.available();
				if(length == 0 && "content".equalsIgnoreCase(mUri.getScheme()))
					length = ((ContentStorageFileEditor)FileEditorFactory.getFileEditorForUrl(mUri, ArchosUtils.getGlobalContext())).getSize();

			}  catch (Exception e) {
				e.printStackTrace();

			}

		}

		private void handleResponse(Socket socket) throws TransportException{
			try{
				InputStream inS = socket.getInputStream();
				if(inS == null&&mInS==null)
					return;
				else if(inS == null)
					inS = mInS; //we have already touched the stream

				byte[] buf = new byte[BUFFER_SIZE];
				int rlen =mRlen;
				if(mRlen<0)
					rlen= inS.read(buf, 0, buf.length);
				if(rlen < 0&&mRlen<0)
					return;
				else if (rlen < 0)
					rlen = mRlen; // we already have the length (ssh)

				// Create a BufferedReader for parsing the header.
				ByteArrayInputStream hbis = new ByteArrayInputStream(buf, 0, rlen);
				BufferedReader hin = new BufferedReader(new InputStreamReader(hbis));
				Properties pre = new Properties();

				// Decode the header into params and header java properties
				boolean decode = decodeHeader(socket, hin, pre)!=null;
				if(!decode&&mPre==null)
					return;
				else if(!decode)
					pre = mPre; // properties have already been decoded (ssh)

				String range = pre.getProperty("range");

				Properties headers = new Properties();
				if(length!=-1)
					headers.put("Content-Length", String.valueOf(length));
				headers.put("Accept-Ranges", canSeek ? "bytes" : "none");
				long sendCount;
				String status;
				if(range==null || !canSeek) {
					status = "200 OK";
					sendCount = length;
				}else {
					if(!range.startsWith("bytes=")){
						sendError(socket, HTTP_416, null);
						return;
					}
					if(debug)
						Log.d(TAG,"handleResponse : "+range);
					range = range.substring(6);
					long startFrom = 0, endAt = -1;
					int minus = range.indexOf('-');
					if(minus > 0){
						try{
							String startR = range.substring(0, minus);
							startFrom = Long.parseLong(startR);
							String endR = range.substring(minus + 1);
							endAt = Long.parseLong(endR);
						}catch(NumberFormatException nfe){}
					}

					if(startFrom >= length){
						sendError(socket, HTTP_416, null);
						inS.close();
						return;
					}
					if(endAt < 0)
						endAt = length - 1;
					sendCount = (endAt - startFrom + 1);
					if(sendCount < 0)
						sendCount = 0;
					status = "206 Partial Content";

					/* else
            	   is.skip(startFrom);*/
            	   headers.put("Content-Length", "" + sendCount);

            	   String rangeSpec = "bytes " + startFrom + "-" + endAt + "/" + length;
            	   headers.put("Content-Range", rangeSpec);
				}
				headers.put("Access-Control-Allow-Origin", "*");
				sendResponse(socket, status, fileMimeType, headers, is, sendCount, buf, null);
				if(debug) Log.d(TAG,"Http stream finished");
			}catch(IOException ioe){
				if(debug)
					Log.w(TAG, ioe);
				try{
					sendError(socket, HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
				}catch(Throwable t){
				}
			}catch(InterruptedException ie){
				// thrown by sendError, ignore and exit the thread
				if(debug)
					Log.w(TAG, ie);
			}
		}

		/**
		 * decode header and returns requested path
		 * @param socket
		 * @param in
		 * @param pre
		 * @return
		 * @throws InterruptedException
		 */
		private String decodeHeader(Socket socket, BufferedReader in, Properties pre) throws InterruptedException{
			String path=null;
			try{
				// Read the request line
				String inLine = in.readLine();
				if(inLine == null)
					return null;
				StringTokenizer st = new StringTokenizer(inLine);
				if(!st.hasMoreTokens())
					sendError(socket, HTTP_BADREQUEST, "Syntax error");

				String method = st.nextToken();
				if(!method.equals("GET"))
					return null;

				if(!st.hasMoreTokens())
					sendError(socket, HTTP_BADREQUEST, "Missing URI");
				path = st.nextToken();
				while(true) {
					String line = in.readLine();
					if(line==null)
						break;
					if(debug && line.length()>0)
						Log.d(TAG, "decodeHeader "+line);
					int p = line.indexOf(':');
					if(p<0)
						continue;
					final String atr = line.substring(0, p).trim().toLowerCase();
					final String val = line.substring(p + 1).trim();
					pre.put(atr, val);
				}
			}catch(IOException ioe){
				sendError(socket, HTTP_INTERNALERROR, "SERVER INTERNAL ERROR: IOException: " + ioe.getMessage());
			}
			return path;
		}
	}

	private boolean isResourcePoster(Uri posterLocalUri) {
		return posterLocalUri.getScheme().equals("resource");
	}


	/**
	 * @param fileName is display name appended to Uri, not really used (may be null), but client may display it as file name.
	 * @return Uri where this stream listens and servers.
	 */
	public Uri getUri(String fileName){
		int port = serverSocket.getLocalPort();
		String url = "http://localhost:"+port;
		if(fileName!=null)
			url += '/'+fileName;
		return Uri.parse(url);
	}

	public void close(){
		if (debug) Log.d(TAG,"Closing stream over http");
		try{
			serverSocket.close();
		}catch(Exception e){
			if (debug)
				Log.w(TAG, e);
		}
	}

	/**
	 * Returns an error message as a HTTP response and
	 * throws InterruptedException to stop further request processing.
	 */
	private void sendError(Socket socket, String status, String msg){
		try {
			sendResponse(socket, status, "text/plain", null, null, 0, null, msg);
		} catch (IOException e) {}
	}

	private void copyStream(InputStream in, OutputStream out, byte[] tmpBuf, long maxSize) throws IOException{
		if (debug) Log.d(TAG, "copyStream");
		int count;

		while(maxSize>0){
			count = (int) Math.min(maxSize, (long)tmpBuf.length);
			count = in.read(tmpBuf, 0, count);
			if(count<0)
				break;
			out.write(tmpBuf, 0, count);
			out.flush();
			maxSize -= count;
		}
	}
	/**
	 * Sends given response to the socket, and closes the socket.
	 */
	private void sendResponse(Socket socket, String status, String mimeType, Properties header, InputStream isInput, long sendCount, byte[] buf, String errMsg) throws IOException {
		if (debug) Log.d(TAG, "sendResponse");
		BufferedInputStream bin = null;
		try{
			OutputStream out = socket.getOutputStream();
			PrintWriter pw = new PrintWriter(out);
			bin = new BufferedInputStream(isInput, BUFFER_SIZE*10);
			{
				String retLine = "HTTP/1.0 " + status + " \r\n";
				pw.print(retLine);
			}
			if(mimeType!=null) {
				String mT = "Content-Type: " + mimeType + "\r\n";
				pw.print(mT);
			}
			if(header != null){
				Enumeration<?> e = header.keys();
				while(e.hasMoreElements()){
					String key = (String)e.nextElement();
					String value = header.getProperty(key);
					String l = key + ": " + value + "\r\n";
					if(debug) Log.d(TAG, "sendResponse : " + l);
					pw.print(l);
				}
			}
			pw.print("\r\n");
			pw.flush();
			if(isInput != null){
				copyStream(bin, out, buf, sendCount);
			}
			else if(errMsg!=null) {
				pw.print(errMsg);
				pw.flush();
			}
			out.flush();
			out.close();
		} catch(IOException e){
			if(debug)
				Log.d(TAG, "sendResponse ",e);
		}finally {
			try{
				socket.close();
			}catch(Throwable t){}
			if (bin != null)
				try{
					bin.close();
					
				}catch(Throwable t){}
		}
	}
}



