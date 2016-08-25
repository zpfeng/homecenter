package hc.util;

import hc.App;
import hc.core.ContextManager;
import hc.core.IConstant;
import hc.core.IContext;
import hc.core.L;
import hc.core.MsgBuilder;
import hc.core.RootConfig;
import hc.core.RootServerConnector;
import hc.core.util.ByteUtil;
import hc.core.util.ExceptionReporter;
import hc.core.util.HCURLUtil;
import hc.core.util.LogManager;
import hc.core.util.StringUtil;
import hc.core.util.URLEncoder;
import hc.j2se.HCAjaxX509TrustManager;
import hc.server.HCActionListener;

import java.awt.BorderLayout;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Enumeration;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

public class HttpUtil {
	static {
		//注意：不适用于Android服务器
		System.setProperty("sun.net.client.defaultConnectTimeout", "8000");//原值为5000，考虑SSL增至8000
		System.setProperty("sun.net.client.defaultReadTimeout", "8000");//原值为5000，考虑SSL增至8000
	}
	
	public static boolean checkExistNetworkInterface(final String name) {
		try {
			if (NetworkInterface.getByName(name) != null) {
				return true;
			}
		} catch (final SocketException e) {
			ExceptionReporter.printStackTrace(e);
		}
		return false;
	}
	
	public static String getHtmlLineStartTag(){
		return " <STRONG>·</STRONG> ";
	}
	
	public static boolean isValidEmail(final String email){
	      final Pattern p = Pattern.compile("\\w+([-+.]\\w+)*@\\w+([-.]\\w+)*\\.\\w+([-.]\\w+)*");
	      final Matcher m = p.matcher(email);
	      if (m.find()){
	    	  return true;
	      }
	      return false;
	}

	public static InetAddress getInetAddress(final String dispname) {
		try {
			final Enumeration<NetworkInterface> ifaces = NetworkInterface
					.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				final NetworkInterface iface = ifaces.nextElement();
				if (iface.getDisplayName().startsWith(dispname, 0)) {
					final Enumeration<InetAddress> iaddresses = iface
							.getInetAddresses();
					while (iaddresses.hasMoreElements()) {
						final InetAddress iaddress = iaddresses.nextElement();
						if (java.net.Inet4Address.class.isInstance(iaddress)
								|| java.net.Inet6Address.class
										.isInstance(iaddress)) {
							if ((!iaddress.isLoopbackAddress())
									&& (!iaddress.isLinkLocalAddress())) {
								return iaddress;
							}
						}
					}
					return null;
				}
			}
		} catch (final Exception e) {

		}
		return null;
	}

	public static String[] getNetworkInterface() {
		int count = 0;
		final String[] temp = new String[30];
		try {
			final Enumeration<NetworkInterface> ifaces = NetworkInterface
					.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				final NetworkInterface iface = ifaces.nextElement();
				final Enumeration<InetAddress> iaddresses = iface.getInetAddresses();
				while (iaddresses.hasMoreElements()) {
					final InetAddress iaddress = iaddresses.nextElement();
					System.out.println(iface.getDisplayName() + ":"
							+ iface.getName() + ", "
							+ iaddress.getHostAddress());
					if (java.net.Inet4Address.class.isInstance(iaddress)
							|| java.net.Inet6Address.class.isInstance(iaddress)) {
						if ((!iaddress.isLoopbackAddress())
								&& (!iaddress.isLinkLocalAddress())) {
							String displayName = iface.getDisplayName();
							final int idx = displayName.indexOf(" - ");
							if (idx > 0) {
								displayName = displayName.substring(0, idx);
							}
							temp[count++] = displayName + " - "
									+ iface.getName();

						}
					}
				}
			}
		} catch (final Exception e) {
			ExceptionReporter.printStackTrace(e);
		}
		final String[] out = new String[count];
		for (int i = 0; i < out.length; i++) {
			out[i] = temp[i];
		}
		return out;
	}

	public static final String FORWARD_301_FOREVER = "301";
	public static final String FORWARD_302_TEMP = "302";
	private static final String TagLocation = "Location";

	public static byte[] convertStrIp(final String ip) {
		final String[] s = ip.split("\\.");
		final byte[] out = new byte[s.length];
		for (int i = 0; i < out.length; i++) {
			out[i] = (byte) (Integer.parseInt(s[i]));
		}
		return out;
	}

	/**
	 * 停止使用
	 * 
	 * @param url
	 * @return [url, forward_type]
	 */
	public static String[] getForward(final String url) {
		try {
			final URLConnection conn = new URL(url).openConnection();

			if (conn instanceof HttpURLConnection) {
				final HttpURLConnection httpconn = (HttpURLConnection) conn;
				httpconn.setInstanceFollowRedirects(false);
				httpconn.connect();

				final int responseCode = httpconn.getResponseCode();
				httpconn.connect();
				if (responseCode == 200) {
					return null;
				} else if (responseCode == 301 || responseCode == 302) {
					final String forwardUrl = conn.getHeaderField(TagLocation);
					final String resolveURL = getResolveURL(url, forwardUrl);

					final String[] out = new String[2];
					out[0] = resolveURL;
					if (responseCode == 301) {
						out[1] = FORWARD_301_FOREVER;
					} else {
						out[1] = FORWARD_302_TEMP;
					}
					return out;
				}
			}
		} catch (final Exception e) {

		}
		return null;
	}

	public static String getResolveURL(String bURL, String relativeURI) {
		if (relativeURI == null) {
			return null;
		}
		final int i = relativeURI.indexOf("\"");
		if (i > 0) {
			relativeURI = relativeURI.substring(0, i);
		}

		if (bURL.startsWith("http")) {

		} else {
			bURL = "http://" + bURL;
		}

		try {
			relativeURI = new URL(new URL(bURL), relativeURI).toExternalForm();
			relativeURI = ByteUtil.encodeURI(relativeURI, IConstant.UTF_8);
			return relativeURI;
		} catch (final Exception e) {
			return null;
		}
	}
	
	public static String getAjaxForSimu(String url) {
		final boolean isSimu = PropertiesManager.isSimu();
		url = replaceSimuURL(url, isSimu);
		//---------reuseThisCode
		return getAjax(url);
	}
	
	public static String replaceSimuURL(String url, final boolean isSimu) {
		if(isSimu){
			final String hostString = RootServerConnector.IP_192_168_1_102 + ":80";//localhost:80
			url = StringUtil.replace(url, RootServerConnector.HOST_HOMECENTER_MOBI, hostString);//192.168.1.101
			url = StringUtil.replace(url, ":80", RootServerConnector.PORT_8080_WITH_MAOHAO);//192.168.1.101
			url = StringUtil.replace(url, RootServerConnector.PORT_808044X, RootServerConnector.PORT_44X_WITH_MAOHAO);
			url = StringUtil.replace(url, "call.php", "callsimu.php");//192.168.1.101
		}
		
		url = RootServerConnector.convertToHttpAjax(url);
		
		return url;
	}

	private static final int MAX_BLOCK_SIZE = 1024 * 1024;
	
	/**
	 * 如果没有成功，则返回null
	 * 注意：限制最长为MAX_BLOCK_SIZE
	 * @param url_str 支持HomeCenter内部ajax转换和外部https两种url
	 * @return
	 */
	public static String getAjax(final String url_str) {
//		if(PropertiesManager.isSimu()){//由于RootRelayReceiveServer，所以不能使用App.isSimu
//			L.V = L.O ? false : LogManager.log("getAjax : " + url_str);
//		}
		
		URL url = null;
		try {
			url = new URL(url_str);
			final URLConnection conn = url.openConnection();
			if(IConstant.isHCServer()){
				conn.setConnectTimeout(HCURLUtil.HTTPS_CONN_TIMEOUT);
				conn.setReadTimeout(HCURLUtil.HTTPS_READ_TIMEOUT);
			}else{
				//Relay Server
				conn.setConnectTimeout(10 * 1000);//有可能服务重启时，先加载RelayServer
				conn.setReadTimeout(8 * 1000);
			}
			
			return getAjax(url, conn);
		} catch (final Throwable e) {
			if(url.getProtocol().equals("https")){
				if(url.getHost().equals(RootServerConnector.HOST_HOMECENTER_MOBI)){
					if(e.getClass().getName().indexOf("SSL") >= 0){//SSLHandshakeException
						//服务器无SSL私钥：java.security.cert.CertificateException: No subject alternative DNS name matching unioncard.mobi found.
						//客户端无SSL公钥：sun.security.validator.ValidatorException: PKIX path building failed: sun.security.provider.certpath.SunCertPathBuilderException: unable to find valid certification path to requested target
						LogManager.errToLog("HTTPS error [" + e.getMessage() + "], maybe ajax.crt/ajax.key is missing or invalid! [" + url_str + "]");
					}
					ExceptionReporter.printStackTrace(e);
				}
			}
			L.V = L.O ? false : LogManager.log("http execption : " + e.getMessage());
		}
		return null;
	}

	public static String getAjax(final URL url, final URLConnection conn) throws Exception {
		HCAjaxX509TrustManager.setAjaxSSLSocketFactory(url, conn);
		
		if (conn instanceof HttpURLConnection) {//HttpsURLConnection extends HttpURLConnection
			final HttpURLConnection httpconn = (HttpURLConnection) conn;
			httpconn.setInstanceFollowRedirects(true);
			httpconn.connect();
			
//				if(httpconn instanceof HttpsURLConnection){
//					final HttpsURLConnection httpsConn = (HttpsURLConnection)conn;
//					final Certificate[] serverCerts = httpsConn.getServerCertificates();
//					if(serverCerts != null){
//						for (int i = 0; i < serverCerts.length; i++) {
//							System.out.println(serverCerts[i]);
//						}
//					}
//				}
			
			final int responseCode = httpconn.getResponseCode();
			if (responseCode == 200) {
				int expectedLength = conn.getContentLength();
				if (expectedLength == 0) {
					// 长度为0的特殊情形
					httpconn.disconnect();
					return "";
				}
				InputStream in;
				in = httpconn.getInputStream();
				if (expectedLength == -1) {
					expectedLength = 1024;
				} else if (expectedLength > MAX_BLOCK_SIZE) {
					expectedLength = MAX_BLOCK_SIZE;
				}
				byte[] buf = new byte[expectedLength];
				int n;
				int total = 0;

				while ((n = in.read(buf, total, buf.length - total)) != -1) {
					total += n;
					if (total == buf.length) {
						// try to read one more character
						final int c = in.read();
						if (c == -1)
							break; // EOF, we're done
						else {
							if (buf.length * 2 <= MAX_BLOCK_SIZE) {
								// need more space in array. Double the
								// array, but don't make
								// it bigger than maxBytes.
								final byte[] newbuf = new byte[buf.length * 2];
								System.arraycopy(buf, 0, newbuf, 0,
										buf.length);
								buf = newbuf;
								buf[total++] = (byte) c;
							} else {
								break;
							}
						}
					}
				}

				in.close();
				httpconn.disconnect();

				return new String(buf, 0, total, IConstant.UTF_8);

			} else {
				httpconn.disconnect();
			}
		}
		return null;
	}

	public static String getLocalIP(){
		InetAddress inet;
		try {
			inet = InetAddress.getLocalHost();
	        return inet.getHostAddress();
		} catch (final UnknownHostException e) {
			ExceptionReporter.printStackTrace(e);
		}
		return null;
	}

	public static InetAddress getLocal(){
//		try {
//			因为有可能返回loop型，所以关闭本操作
//			InetAddress.anyLocalAddress();
//			return (InetAddress.getLocalHost());
//		} catch (Throwable e) {
			// sometimes get this when changing host name
			// return first non-loopback one
			InetAddress ia = getServerInetAddress(true);
			if(ia != null){
				return ia;
			}else{
				ia = getServerInetAddress(false);
			}
			
			if(ia != null){
				return ia;
			}else{
				try {
					return (InetAddress.getByName("127.0.0.1"));
				} catch (final UnknownHostException e1) {
				}
			}
			return null;
//		}
	}
	
	public static final String AUTO_DETECT_NETWORK = "auto detect";
	
	public static InetAddress getInetAddressByDeviceName(final String name){
		return filerInetAddress(getNetworkInterface(name));
	}
	
	private static NetworkInterface getNetworkInterface(final String name){
		try{
			final Enumeration nis = NetworkInterface.getNetworkInterfaces();
			while (nis.hasMoreElements()) {
				final NetworkInterface ni = (NetworkInterface) nis.nextElement();
				if(ni.getDisplayName().equals(name)){
					return ni;
				}
			}	
		}catch (final Exception e) {
		}
		return null;
	}
	
	public static Vector<String> getAllNetworkInterfaces(){
		final Vector<String> v = new Vector<String>();
		v.add(AUTO_DETECT_NETWORK);
		
		try{
			final Enumeration nis = NetworkInterface.getNetworkInterfaces();
			while (nis.hasMoreElements()) {
				final NetworkInterface ni = (NetworkInterface) nis.nextElement();
				v.add(ni.getDisplayName());
			}	
		}catch (final Exception e) {
		}
		return v;
	}

	/**
	 * 过滤loop型、IPv6型，及选择性PointToPoint
	 */
	public static InetAddress getServerInetAddress(final boolean mustP2P) {
		try {
			final Enumeration nis = NetworkInterface.getNetworkInterfaces();
			while (nis.hasMoreElements()) {
				final NetworkInterface ni = (NetworkInterface) nis.nextElement();
				if(mustP2P && (ni.isPointToPoint() == false)){
					continue;
				}
				final InetAddress ia = filerInetAddress(ni);
				if(ia != null){
					return ia;
				}
			}
		} catch (final Throwable f) {
		}
		return null;
	}

	public static InetAddress filerInetAddress(final NetworkInterface ni) {
		try{
			final Enumeration addresses = ni.getInetAddresses();
			while (addresses.hasMoreElements()) {
				final InetAddress address = (InetAddress) addresses.nextElement();
				if (address.isLoopbackAddress() || address instanceof Inet6Address) {
					continue;
				}
				return (address);
			}
		}catch (final Throwable e) {
		}
		return null;
	}
	
	/**
	 * 非IPv6型的本地InetAddress
	 * @return
	 */
	public static InetAddress getLocalOutAddress(final boolean isIPv6) {
		try {
			final Enumeration<NetworkInterface> ifaces = NetworkInterface
					.getNetworkInterfaces();
			while (ifaces.hasMoreElements()) {
				final NetworkInterface iface = ifaces.nextElement();
				final Enumeration<InetAddress> iaddresses = iface.getInetAddresses();
				while (iaddresses.hasMoreElements()) {
					final InetAddress iaddress = iaddresses.nextElement();
					if (!iaddress.isLoopbackAddress()
							&& !iaddress.isSiteLocalAddress()
							&& !iaddress.isLinkLocalAddress()) {
						if (isIPv6 == false) {
							if(java.net.Inet4Address.class.isInstance(iaddress)){
								return iaddress;
							}
						}else if(java.net.Inet6Address.class.isInstance(iaddress)){
							return iaddress;
						}
					}
				}
			}
		} catch (final Exception e) {
			ExceptionReporter.printStackTrace(e);
		}
		return null;
	}

	public static ByteBuffer buildByteBuffer() {
		return ByteBuffer.allocate(1024);
	}
	
	/**
	 * 
	 * @param langURL suchas pc/faq.htm#item7
	 * @return
	 */
	public static boolean browseLangURL(final String langURL){
		return HttpUtil.browse(langURL);
	}

	public static String buildLangURL(final String langURL, final String lang)
			throws UnsupportedEncodingException {
		String url = URLEncoder.encode("https://homecenter.mobi/_lang_/" + langURL, IConstant.UTF_8);
		if(lang == null){
			url = "?to=" + url;
		}else{
			url = "?lang=" + lang + "&to=" + url;
		}
		return "https://homecenter.mobi/gotolang.php" + url;
	}

	public static boolean browse(final String donateURL) {
		final java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
		if( !desktop.isSupported( java.awt.Desktop.Action.BROWSE ) ) {
		    App.showMessageDialog(null, 
		    		"Desktop doesn't support the browse open action (fatal)\r\n" +
		    		"Please browse URL:" + donateURL,
		    		"Unable Open URL", JOptionPane.ERROR_MESSAGE);
		    return false;
		}
		
		try{
			desktop.browse(new java.net.URI(donateURL));
		}catch(final Exception ex) {
		    App.showMessageDialog(null, ex.getMessage(), 
		    		(String)ResourceUtil.get(IContext.ERROR), JOptionPane.ERROR_MESSAGE);
		    return false;
		}

		return true;
	}

	/**
	 * 批下载文件，单线程方式。如果部分下载成功，即不是全部成功，将返回false，并删除成功部分，维持初始状态。
	 * @param fs
	 * @param base
	 * @return
	 */
	public static boolean download(final String[] fs, final String base) {
		try{
		final File[] oldFs = new File[fs.length];
		final File[] newFs = new File[fs.length];
		boolean isError = false;
		
		for (int i = 0; i < fs.length; i++) {
			final String fileName = fs[i];
			final String tmpFileName = "tmpV" + fileName;
			final File tmpDownFile = new File(ResourceUtil.getBaseDir(), fileName);
			final File filev = new File(ResourceUtil.getBaseDir(), tmpFileName);
			oldFs[i] = tmpDownFile;
			newFs[i] = filev;
			if(HttpUtil.downloadFile(tmpDownFile, filev, fileName, base) == false){
				isError = true;
				break;
			}
		}
		
		if(isError == false){
			for (int i = 0; i < newFs.length; i++) {
				if(oldFs[i].delete() && newFs[i].renameTo(oldFs[i])){
				}else{
					isError = true;
					break;
				}
			}
		}
		
		if(isError){
			for (int j = 0; j <= fs.length; j++) {
				newFs[j].delete();
			}
			return false;
		}
		
		System.gc();
		
		return true;
		}catch (final Exception e) {
			
		}
		return false;
	}

	private static boolean downloadFile(final File starter, final File filev, final String fileName, final String base) {
		try{
			if((starter.exists() == false) || starter.setWritable(true)){
				InputStream is = null;
				FileOutputStream os = null;
				try{
					final URL url = new URL(base + fileName);
					is = url.openStream();
					os = new FileOutputStream(filev);
					final byte[] buf = new byte[1024]; //optimize the size of buffer to your need
				    int num;
				    while((num = is.read(buf)) != -1){
				        os.write(buf, 0, num);
				    }
				}finally{
					try{
						is.close();
					}catch (final Exception e) {
					}
					try{
						os.close();
					}catch (final Exception e) {
					}
				}
				return true;
			}else{
				return false;
			}
		}catch (final Exception e) {
		}
		return false;
	}

	public static String replaceIPWithHC(final String ip){
		final String root = RootConfig.getInstance().getProperty(RootConfig.p_RootRelayServer);
		if(ip.indexOf(root) >= 0){
			return "homecenter.mobi[" + ip + "]";
		}else{
			return ip;
		}
	}

	/**
	 * 注意：本方法必须在线程内同步完成
	 * @param isQuery
	 * @param parent
	 */
	public static void notifyStopServer(final boolean isQuery, final JFrame parent) {
		if(ContextManager.cmStatus == ContextManager.STATUS_SERVER_SELF){
			if(isQuery){
				final JPanel panel = new JPanel(new BorderLayout());
				panel.add(new JLabel("<html>service/configuration is changed and mobile is connecting," +
						"<BR>click '" + (String) ResourceUtil.get(IContext.OK) + "' to break off current mobile connection!" +
						"</html>", App.getSysIcon(App.SYS_INFO_ICON), SwingConstants.LEADING), BorderLayout.CENTER);
				panel.add(new JLabel("<html><BR><strong>"+(String)ResourceUtil.get(IContext.TIP)+"</strong> : " +
						"<BR>it is <strong>NOT</strong> required to restart HomeCenter server.</html>"), BorderLayout.SOUTH);
				final ActionListener listener = new HCActionListener(new Runnable() {
					@Override
					public void run() {
						doNotifyMobile();
					}
				}, App.getThreadPoolToken());
				App.showCenterPanelMain(panel, 0, 0, "break off connection of mobile", false, null, null, listener, listener, parent, true, false, null, false, false);
			}else{
				doNotifyMobile();
			}
		}
	}

	private static void doNotifyMobile() {
		ContextManager.getContextInstance().send(MsgBuilder.E_TAG_SHUT_DOWN_BETWEEN_CS, RootServerConnector.getHideToken());
		
		//在模拟环境中，由于UDP的后滞性，E_TAG_SHUT_DOWN_BETWEEN_CS基本不被送出
		
		//等待数据包完全发送出去
		try{
			Thread.sleep(1000);
		}catch (final Exception e) {
		}
	}
	
	public static boolean download(final File file, final URL url){
		file.delete();
		
		RandomAccessFile raf = null;
		try {
			raf = new RandomAccessFile(file, "rw");
		
			final int start = 0;
	        int tryNum = 0;
			final int maxTryNum = 3;
			int downloadBS = 0;
			while(tryNum < maxTryNum){
	            try{
		        	final HttpURLConnection conn = (HttpURLConnection) url.openConnection();  
		            conn.setRequestMethod("GET");  
//		            conn.setReadTimeout(0);//无穷  
					conn.setRequestProperty("Range", "bytes=" + start + "-");  
		            if (conn.getResponseCode() == 206) {  
		                raf.seek(start + downloadBS);  
		                final InputStream inStream = conn.getInputStream();  
		                final byte[] b = new byte[1024 * 10];  
		                int len = 0;  
		                while ((len = inStream.read(b)) != -1) {  
		                	raf.write(b, 0, len);  
		                    downloadBS += len;
		                }  
		            }else{
		            	L.V = L.O ? false : LogManager.log("http/206 not support download file.");
		            	break;
		            }
		            conn.disconnect();
		            return true;
	            }catch (final Exception e) {
	            	ExceptionReporter.printStackTrace(e);
	            	L.V = L.O ? false : LogManager.log("try more time to download.");
	            	tryNum++;
	            	try{
	            		Thread.sleep(1000);
	            	}catch (final Exception ex) {
					}
				}
	        }
		}catch(final Throwable t){
		}finally{
			try{
				raf.close();
			}catch (final Throwable e) {
			}
		}
		return false;
	}
	
	public static final String encodeFileName(final String fileName){
		return fileName;
	}
	
	public static final String decodeFileName(final String fileName){
		return fileName;
	}

	public static final String encodeHCHexString(final String src){
		final StringBuilder sb = new StringBuilder();
		
		byte[] bs;
		try {
			bs = src.getBytes(IConstant.UTF_8);
		} catch (final UnsupportedEncodingException e) {
			bs = src.getBytes();
		}
		
		for (int j = 0; j < bs.length; j++) {
			String hexString = Integer.toHexString((bs[j]) & 0xFF);//必须是小写，因为文件控制模块依赖于此逻辑
			if(hexString.length() < 2){
				hexString = "0" + hexString;
			}
			sb.append(hexString);
		}
		
		return sb.toString();
	}

	public static final String decodeHCHexString(final String hexString){
		final int length = hexString.length() / 2;   
	    final char[] hexChars = hexString.toLowerCase().toCharArray();   
	    final byte[] bs = new byte[length];   
	    for (int i = 0; i < length; i++) {   
	        final int pos = i * 2;   
	        bs[i] = (byte) (charToByte(hexChars[pos]) << 4 | charToByte(hexChars[pos + 1]));   
	    }   
	    
	    try {
			return new String(bs, IConstant.UTF_8);
		} catch (final UnsupportedEncodingException e) {
			return new String(bs);
		}
	}
	
	private static final char[] ZERO_TO_DEF = {'0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'};
	
	private static final byte charToByte(final char c) {   
		for (int i = 0; i < ZERO_TO_DEF.length; i++) {
			if(c == ZERO_TO_DEF[i]){
				return (byte)i;
			}
		}
	    return (byte)0;   
	} 
}
