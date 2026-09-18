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

import static com.archos.filecorelibrary.FileUtils.caughtException;

import android.net.Uri;
import android.database.Cursor;
import android.provider.OpenableColumns;

import com.archos.environment.ArchosUtils;
import com.archos.filecorelibrary.contentstorage.ContentStorageFileEditor;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.SftpException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * This is simple HTTP local server for streaming InputStream to apps which are capable to read data from url.
 * Random access input stream is optionally supported, depending if file can be opened in this mode. 
 */
public class StreamOverHttp {
	private static final Logger log = LoggerFactory.getLogger(StreamOverHttp.class);

	private final Uri mUri;
	private final String mName;

	private String fileMimeType;
	private static final int BUFFER_SIZE = 8192;
	private ServerSocket serverSocket;
	private Thread mainThread;
	private MetaFile2 mMetaFile;
	private final AtomicInteger mNextRequestId = new AtomicInteger(1);
	private volatile int mActiveMediaRequestId = 0;
	private volatile HttpSession mActiveMediaSession;
	private final Object mSessionLock = new Object();
	private final Set<HttpSession> mSessions = new HashSet<>();
	private boolean mClosed;
	private static final int MAX_SESSIONS = 16;
	private static final int MAX_HEADER_BYTES = 65536;

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
				try {
					while(true) {
						Socket accept = serverSocket.accept();
						new HttpSession(accept,fileMimeType);
					}
				} catch(IOException e) {
					caughtException(e, "StreamOverHttp:StreamOverHttp", "IOException for " + mUri);
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
                try {
                    while(true) {
                        Socket accept = serverSocket.accept();
                        new HttpSession(accept,fileMimeType);
                    }
                } catch(IOException e) {
					caughtException(e, "StreamOverHttp:StreamOverHttp", "IOException for " + mUri);
				}
            }

        });
        mainThread.setName("Stream over HTTP");
        mainThread.setDaemon(true);
        mainThread.start();
    }

	private static final String[] SUBTITLES_ARRAY = { "idx", "smi", "ssa", "ass", "srr", "srt", "sub", "mpl", "txt","xml", "vtt"};

	public List<MetaFile2> getSubtitleList(Uri video) throws SftpException, AuthenticationException, JSchException, IOException {
		if(mSubList!=null)
			return mSubList;
		final Uri parentUri = FileUtils.getParentUrl(video);
		final String videoFileName = FileUtils.getName(video);
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
				if (subtitlesExtensions.contains(extension))
					subs.add(item);
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
		return getUri(FileUtils.getName(posterLocalUri));
	}

	private static class RequestException extends IOException {
		final String status;
		RequestException(String status, String message) { super(message); this.status = status; }
	}

	private class HttpSession implements Runnable {
		private boolean canSeek;
		private InputStream is; // publication and ownership transfers use this session's monitor
		private final Socket socket;
		private final int requestId;
		private final Thread worker;
		private final Properties requestHeaders = new Properties();
		private String fileMimeType;
		private long length = -1;
		private long requestedStart;
		private long requestedEnd = -1;
		private boolean partialResponse;
		private boolean supersedableMediaRequest;
		private volatile boolean forceClosed;
		private boolean responseStarted;
		private int cleanupTasks = 1; // guarded by mSessionLock; includes the worker

		HttpSession(Socket socket, String fileMimeType) {
			this.socket = socket;
			this.fileMimeType = fileMimeType;
			requestId = mNextRequestId.getAndIncrement();
			worker = new Thread(this, "Http response");
			worker.setDaemon(true);
			synchronized (mSessionLock) {
				if (mClosed || mSessions.size() >= MAX_SESSIONS) {
					closeSocket();
					return;
				}
				mSessions.add(this);
				worker.start();
			}
		}

		private void markActiveMediaRequest() throws IOException {
			if (!supersedableMediaRequest) return;
			HttpSession previous;
			synchronized (mSessionLock) {
				if (mClosed || requestId < mActiveMediaRequestId) throw new IOException("Request superseded");
				previous = mActiveMediaSession;
				mActiveMediaSession = this;
				mActiveMediaRequestId = requestId;
			}
			if (previous != null && previous != this) previous.cancel();
		}

		private boolean isCancelled() {
			return forceClosed || (supersedableMediaRequest && requestId < mActiveMediaRequestId);
		}

		private void closeSocket() {
			try { socket.close(); } catch (IOException ignored) { }
		}

		private void closeInput(InputStream input) {
			if (input == null) return;
			try { input.close(); }
			catch (Exception e) { caughtException(e, "StreamOverHttp:closeInput", "Closing upstream input"); }
		}

		private synchronized InputStream takeInput() {
			InputStream input = is;
			is = null;
			return input;
		}

		private void publishInput(InputStream input) throws IOException {
			if (input == null) throw new IOException("No upstream input");
			synchronized (this) {
				if (!isCancelled()) { is = input; return; }
			}
			closeInput(input);
			throw new IOException("Request cancelled while opening input");
		}

		private void cancel() {
			synchronized (this) {
				if (forceClosed) return;
				forceClosed = true;
			}
			closeSocket();
			worker.interrupt();
			// Some backends need close() to unblock read(), and close itself may wait
			// for network I/O. Never make playback stop or the replacement request wait.
			InputStream input;
			synchronized (this) {
				input = takeInput();
				if (input != null) {
					synchronized (mSessionLock) { cleanupTasks++; }
				}
			}
			if (input != null) {
				Thread closer = new Thread(() -> {
					try { closeInput(input); } finally { finishTask(); }
				}, "Http input close");
				closer.setDaemon(true);
				closer.start();
			}
		}

		private void finishTask() {
			synchronized (mSessionLock) {
				// Keep stalled closes in the admission limit as well as stalled reads.
				if (--cleanupTasks == 0) mSessions.remove(this);
				if (mActiveMediaSession == this) mActiveMediaSession = null;
			}
		}

		public void run() {
			try {
				openInputStream();
				handleResponse();
			} catch (Exception e) {
				if (!isCancelled()) {
					caughtException(e, "StreamOverHttp:HttpSession", "Request failed for " + mUri);
					if (!responseStarted) sendError(socket,
							e instanceof RequestException ? ((RequestException)e).status : HTTP_INTERNALERROR,
							"Unable to serve request");
				}
			} finally {
				closeSocket();
				closeInput(takeInput());
				finishTask();
			}
		}

		private String readRequest() throws IOException {
			socket.setSoTimeout(10000);
			InputStream input = new BufferedInputStream(socket.getInputStream());
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			int tail = 0;
			while (tail != 0x0d0a0d0a) {
				int value = input.read();
				if (value < 0) throw new EOFException("Incomplete HTTP request");
				if (bytes.size() >= MAX_HEADER_BYTES) throw new RequestException(HTTP_BADREQUEST, "Headers too large");
				bytes.write(value);
				tail = (tail << 8) | value;
			}
			socket.setSoTimeout(0);
			String[] lines = new String(bytes.toByteArray(), StandardCharsets.ISO_8859_1).split("\r\n");
			String[] request = lines[0].split(" +");
			if (request.length != 3 || !"GET".equals(request[0]) || !request[2].startsWith("HTTP/1."))
				throw new RequestException(HTTP_BADREQUEST, "Invalid request line");
			for (int i = 1; i < lines.length; ++i) {
				int colon = lines[i].indexOf(':');
				if (colon <= 0) throw new RequestException(HTTP_BADREQUEST, "Invalid header");
				String name = lines[i].substring(0, colon).trim().toLowerCase(Locale.ROOT);
				if ("range".equals(name) && requestHeaders.containsKey(name))
					throw new RequestException(HTTP_416, "Multiple ranges unsupported");
				requestHeaders.setProperty(name, lines[i].substring(colon + 1).trim());
			}
			return Uri.decode(request[1]);
		}

		private void resolveRange() throws IOException {
			String range = requestHeaders.getProperty("range");
			// HTTP permits ignoring Range. Unknown-size/nonseekable inputs are sent
			// from byte zero, delimited by connection close, without invented lengths.
			if (range == null || !canSeek || length < 0) return;
			if (!range.matches("bytes=[0-9]*-[0-9]*")) throw new RequestException(HTTP_416, "Invalid range");
			String[] ends = range.substring(6).split("-", -1);
			try {
				if (ends[0].isEmpty()) {
					long suffix = Long.parseLong(ends[1]);
					if (suffix <= 0) throw new NumberFormatException();
					requestedStart = Math.max(0, length - suffix);
					requestedEnd = length - 1;
				} else {
					requestedStart = Long.parseLong(ends[0]);
					requestedEnd = ends[1].isEmpty() ? length - 1 : Math.min(Long.parseLong(ends[1]), length - 1);
				}
			} catch (NumberFormatException e) { throw new RequestException(HTTP_416, "Invalid range"); }
			if (requestedStart >= length || requestedEnd < requestedStart) throw new RequestException(HTTP_416, "Unsatisfiable range");
			partialResponse = true;
		}

		private void openInputStream() throws Exception {
			String path = readRequest();
			boolean isAskingPoster = false;
			canSeek = true;
			if(mMetaFile==null&&mUri!=null) {
				try {
					mMetaFile = MetaFile2Factory.getMetaFileForUrl(mUri);
				} catch(Exception e) {
					caughtException(e, "StreamOverHttp:openInputStream", "InterruptedException retrieving metafile");
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
				} else {
					if (!mName.equals(name)) {
						List<MetaFile2> subs = getSubtitleList(mUri);
						String extension = MimeUtils.getExtension(path);
						for (MetaFile2 sub : subs) {
							if (sub.getName().equals(name)) {
								metaFile2 = sub;
								break;
							}
							if (sub.getExtension().equals(extension))
								subFallback = sub;
						}
						if (metaFile2 == null)
							metaFile2 = subFallback;
						if (metaFile2 != null)
							canSeek = false;
					}
				}
			}

			if(metaFile2==null&&!isAskingPoster)
				metaFile2 = mMetaFile;

			supersedableMediaRequest = !isAskingPoster && metaFile2 == mMetaFile;
			markActiveMediaRequest();
			if (isCancelled()) throw new IOException("Request cancelled");
			if (isAskingPoster && isResourcePoster(mPosterLocalUri)) {
				canSeek = false;
				publishInput(ArchosUtils.getGlobalContext().getResources().openRawResource(mPosterGenericResource));
				return;
			}
			Uri target = metaFile2 != null ? metaFile2.getUri() : mUri;
			FileEditor editor = FileEditorFactory.getFileEditorForUrl(target, ArchosUtils.getGlobalContext());
			if (metaFile2 != null && metaFile2.length() > 0) length = metaFile2.length();
			try {
				long editorLength = editor.length();
				if (editorLength >= 0) length = editorLength;
				if (length < 0 && editor instanceof ContentStorageFileEditor) {
					try (Cursor cursor = ArchosUtils.getGlobalContext().getContentResolver().query(
							target, new String[] { OpenableColumns.SIZE }, null, null, null)) {
						if (cursor != null && cursor.moveToFirst()) {
							int column = cursor.getColumnIndex(OpenableColumns.SIZE);
							if (column >= 0 && !cursor.isNull(column)) length = cursor.getLong(column);
						}
					}
				}
			} catch (Exception e) {
				// Length is optional; absence must not prevent sequential playback.
				log.debug("Unable to determine stream length", e);
			}
			resolveRange();
			try {
				if (isCancelled()) throw new IOException("Request cancelled");
				publishInput(editor.getInputStream(requestedStart));
				// HTTP/WebDAV editors may learn the total size only from opening
				// their response. Keep range support when metadata did not know it.
				if (length < 0) {
					try { length = editor.length(); }
					catch (Exception e) { log.debug("Stream length still unknown", e); }
					if (length >= 0) {
						resolveRange();
						if (requestedStart > 0) {
							closeInput(takeInput());
							if (isCancelled()) throw new IOException("Request cancelled");
							publishInput(editor.getInputStream(requestedStart));
						}
					}
				}
			} catch (IOException e) {
				if (!"Illegal seek".equals(e.getMessage())) throw e;
				canSeek = false;
				partialResponse = false;
				requestedStart = 0;
				publishInput(editor.getInputStream());
			}
		}

		private void handleResponse() throws IOException {
			if (isCancelled()) return;
			Properties headers = new Properties();
			headers.setProperty("Accept-Ranges", canSeek && length >= 0 ? "bytes" : "none");
			headers.setProperty("Connection", "close");
			headers.setProperty("Access-Control-Allow-Origin", "*");
			long count = partialResponse ? requestedEnd - requestedStart + 1 : length;
			if (count >= 0) headers.setProperty("Content-Length", Long.toString(count));
			if (partialResponse) headers.setProperty("Content-Range", "bytes " + requestedStart + "-" + requestedEnd + "/" + length);
			InputStream input;
			synchronized (this) { input = is; }
			if (input == null || isCancelled()) return;
			responseStarted = true;
			sendResponse(socket, partialResponse ? "206 Partial Content" : "200 OK", fileMimeType,
					headers, input, count, new byte[BUFFER_SIZE], null, this);
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

	public Uri getEncodedUri(String fileName){
		int port = serverSocket.getLocalPort();
		String url = "http://localhost:"+port;
		if(fileName!=null) {
			try {
				// URL encode the filename to handle spaces and special characters
				String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());
				url += '/'+encodedFileName;
			} catch (Exception e) {
				log.warn("Failed to URL encode filename: {}, using original", fileName, e);
				url += '/'+fileName;
			}
		}
		return Uri.parse(url);
	}

	public void close() {
		List<HttpSession> sessions;
		synchronized (mSessionLock) {
			mClosed = true;
			sessions = new ArrayList<>(mSessions);
		}
		try { serverSocket.close(); } catch (IOException ignored) { }
		for (HttpSession session : sessions) session.cancel();
	}

	/**
	 * Returns an error message as a HTTP response and
	 * throws InterruptedException to stop further request processing.
	 */
	private void sendError(Socket socket, String status, String msg){
		try {
			sendResponse(socket, status, "text/plain", null, null, 0, null, msg, null);
		} catch (IOException e) {
			caughtException(e, "StreamOverHttp:sendError", "IOException");
		}
	}

	private void copyStream(InputStream in, OutputStream out, byte[] buffer, long remaining, HttpSession session) throws IOException {
		while (remaining != 0) {
			if (session != null && session.isCancelled()) return;
			int count = in.read(buffer, 0, remaining < 0 ? buffer.length : (int)Math.min(remaining, buffer.length));
			if (count < 0) {
				if (remaining > 0) throw new EOFException("Upstream ended before Content-Length");
				return;
			}
			if (count == 0) throw new IOException("Upstream read made no progress");
			if (session != null && session.isCancelled()) return;
			out.write(buffer, 0, count);
			if (remaining > 0) remaining -= count;
		}
	}

	/**
	 * Sends given response to the socket, and closes the socket.
	 */
	private void sendResponse(Socket socket, String status, String mimeType, Properties header, InputStream isInput, long sendCount, byte[] buf, String errMsg, HttpSession session) throws IOException {
		if (log.isDebugEnabled()) log.debug("sendResponse");
		BufferedInputStream bin = null;
		try {
			OutputStream out = socket.getOutputStream();
			PrintWriter pw = new PrintWriter(out);
			if (isInput != null) bin = new BufferedInputStream(isInput, BUFFER_SIZE*10);
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
					if (log.isDebugEnabled()) log.debug("sendResponse : {}", l);
					pw.print(l);
				}
			}
			pw.print("\r\n");
			pw.flush();
			if (pw.checkError()) throw new IOException("Failed to write response headers");
			if(isInput != null)
				copyStream(bin, out, buf, sendCount, session);
			else if(errMsg!=null) {
				pw.print(errMsg);
				pw.flush();
			}
			out.flush();
			out.close();
		} finally {
			try {
				socket.close();
			} catch(Throwable t) {
				caughtException(t, "StreamOverHttp:sendResponse", "Throwable closing socket");
			}
			// HttpSession owns upstream close, including cancellation during read().
		}
	}
}
