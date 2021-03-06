package hc.server.relay;

import hc.core.ContextManager;
import hc.core.CoreSession;
import hc.core.GlobalConditionWatcher;
import hc.core.HCMessage;
import hc.core.IConstant;
import hc.core.IContext;
import hc.core.IWatcher;
import hc.core.L;
import hc.core.MsgBuilder;
import hc.core.RootServerConnector;
import hc.core.data.DataNatReqConn;
import hc.core.data.DataReg;
import hc.core.util.ExceptionReporter;
import hc.core.util.HCURLUtil;
import hc.core.util.LogManager;
import hc.core.util.Stack;
import hc.server.nio.AcceptReadThread;
import hc.server.nio.ByteBufferCacher;
import hc.server.util.ByteArr;
import hc.server.util.StarterParameter;
import hc.util.ByteArrCacher;
import hc.util.HttpUtil;
import hc.util.PropertiesManager;
import hc.util.TokenManager;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.Vector;

public class RelayManager {

	//按长度进入本数组入口
	public static final ThreeDirNode[] tdn = new ThreeDirNode[MsgBuilder.LEN_MAX_UUID_VALUE];
	static{
		for (int i = 0; i < MsgBuilder.LEN_MAX_UUID_VALUE; i++) {
			tdn[i] = new ThreeDirNode(ThreeDirNode.MID_BYTE);
		}		
	}
	
	private static int getRelayBlockNum(){
		final String num = PropertiesManager.getValue(PropertiesManager.p_RelayBlockNum);
		if(num == null){
			return 200;
		}else{
			try{
				return Integer.parseInt(num);
			}catch (final Throwable e) {
			}
		}
		return 200;
	}
	
	private static final int RelayBlockNum = getRelayBlockNum();
	
	public static short size = 0;
	
	public static boolean isShutdowning = false;
	
	private static final WrapInt serverNum = new WrapInt();
	private static final Runnable refreshServerNum = new Runnable(){
		@Override
		public void run() {
			RootServerConnector.serverNum(serverNum.value, TokenManager.getToken());
		}};

		
	private static boolean loadDisableRelay(){
		final String r = PropertiesManager.getValue("DisableRelay");
		if(r == null || (r.equals(IConstant.TRUE) == false)){
			return false;
		}else{
			return true;
		}
	}
	
	//强制关闭中继服务
	public static final boolean disableRelay = loadDisableRelay();
	
	private static final Stack scCacher = new Stack();
	private static final ByteBufferCacher bufferDirectCacher = AcceptReadThread.bufferDirectCacher;
	private static final ByteBuffer bufferDirect = bufferDirectCacher.getFree();

	private static final ByteBuffer buffer = HttpUtil.buildByteBuffer();
	private static final ByteBuffer unForwarBuffer = buildUnForward();
	private static final ByteBuffer rootBuffer = buildRootBuffer();
	private static final ByteArrCacher byteCache = new ByteArrCacher(MsgBuilder.LEN_MAX_UUID_VALUE);
	private static final IOException BBIOException = new IOException();
	private static final Exception WriteSetOvelException = new Exception("WriteSet overfull, please increase option [RelayBlockNum]");
	
	private static final Selector selector = AcceptReadThread.connectSelector;
	
	private static ByteBuffer buildUnForward(){
		final ByteBuffer bb = ByteBuffer.allocate(MsgBuilder.MIN_LEN_MSG);
		final byte[] bs = bb.array();
		bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_TAG_UN_FORWARD_DATA;
		return bb;
	}
	
	private static ByteBuffer buildRootBuffer(){
		final ByteBuffer bb = ByteBuffer.allocate(MsgBuilder.MIN_LEN_MSG);
		final byte[] bs = bb.array();
		bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_TAG_ROOT;
		return bb;
	}
	
	private static void setRootBufferSubTag(final byte subTag){
		final byte[] bs = rootBuffer.array();
		bs[MsgBuilder.INDEX_CTRL_SUB_TAG] = subTag;
		rootBuffer.clear();
		rootBuffer.limit(MsgBuilder.MIN_LEN_MSG);
	}

	/**
	 * 由于采用数据包直接提取地址法，该法有利于Relay迁移；
	 * 而对于服务器初始上线注册，采用特别Tag
	 * @param ctx 
	 * @param obj
	 */
	public static void relay(final IContext ctx, final SelectionKey key){
		final SessionConnector sc = (SessionConnector)key.attachment();
		final SocketChannel sourceChannel = (SocketChannel)key.channel();
//		if(L.isLogInRelay) {
//			LogManager.log("SelectionKey hashCode:" + key.hashCode());
//		}
		if(sc != null){
//			if(L.isLogInRelay) {
//				LogManager.log("SessionConnector hashCode:" + sc.hashCode());
//			}
			final SocketChannel targetChannel = sc.getTarget(sourceChannel);
			final boolean targetServerOrClient = !sc.isServerChannel(sourceChannel);
			try{
//				if(L.isLogInRelay) {
//					LogManager.log("Data In, Check target Channel Server/Client["+targetServerOrClient+"] is reset["+isTargetReset+"]");
//				}
				if((targetChannel != null)){
					int oldwriteSetSize = sc.getWriteSetSize(targetChannel, targetServerOrClient);
					do{
//						if(L.isLogInRelay) {
//							LogManager.log("try read to DirectBuffer");
//						}
						final int readLen = sourceChannel.read(bufferDirect);
//						if(L.isLogInRelay) {
//							LogManager.log("DirectBuffer readed " + readLen + " bytes.");
//						}
						if(readLen > 0){
							//转发
							bufferDirect.flip();
							
							if(oldwriteSetSize == 0){
//								if(L.isLogInRelay) {
//									LogManager.log("targetChannel is WriteSet Empty, write to target");
//								}
								//直接转发，增加性能
								targetChannel.write(bufferDirect);
								if(bufferDirect.hasRemaining()){
//									if(L.isLogInRelay) {
//										LogManager.log("[RelayCache] write to target direct, but has remain, toWriteBackend");
//									}
									final ByteBuffer copyBB = bufferDirectCacher.getFree();
									copyBB.put(bufferDirect);
									copyBB.flip();
									toWriteBackend(sc, targetChannel, copyBB, targetServerOrClient);
								}else{
//									if(L.isLogInRelay) {
//										LogManager.log("write to target direct, clear");
//									}
								}
							}else{
								if(oldwriteSetSize < RelayBlockNum){
									oldwriteSetSize++;
									
//									if(L.isLogInRelay) {
//										LogManager.log("[RelayCache] targetChannel has WriteSet, toWriteBackend");
//									}
								
									final ByteBuffer copyBB = bufferDirectCacher.getFree();
									copyBB.put(bufferDirect);
									copyBB.flip();
									
									toWriteBackend(sc, targetChannel, copyBB, targetServerOrClient);
								}else{
									throw WriteSetOvelException;
								}
							}
							//清空，重用
							bufferDirect.clear();
						}else if(readLen == -1){
							//可怜的网络性能
							throw BBIOException;
						}else{
//							if(L.isLogInRelay) {
//								LogManager.log("No data read, return");
//							}
							//readLen == 0, 无数据
							return;
						}
					}while(true);
				}else{
					pushMap(ctx, key);
				}
			}catch (final Exception e) {
				bufferDirect.clear();
				if(L.isLogInRelay) {
					LogManager.log("Relay Exception : [" + e.getMessage() + "]");
				}
				//设置当前Channle,转为Reset状态
				//客户端因网络不稳定，导致断线，进入重连模式
				if(L.isLogInRelay) {
					LogManager.log("ServerOrClient["+(!targetServerOrClient)+", isMatched : " + sc.isMatched + ", remotePort:" + sourceChannel.socket().getPort() + "] closed.");
				}
				
				//只要一端被断开，则全部断开.
//				closeClient(sc);	
//				closeServer(sc);
//				sc.setReset(!targetServerOrClient, true);
				RelayManager.closePare(sc, sc.isMatched?false:true);
			}

		}else{
			try{
				if(pushMap(ctx, key)){
					synchronized (serverNumRefreshLock) {
						serverNum.value = (++size);
						if(L.isLogInRelay) {
							LogManager.log("server num : " + serverNum.value);
						}
						ContextManager.getThreadPool().run(refreshServerNum);//不用threadPoolToken
						
						LogManager.flush();
					}
				}
			}catch (final Exception e) {
//				ExceptionReporter.printStackTrace(e);
				//断线
				if(L.isLogInRelay) {
					LogManager.log("lineOff or Exception [" + e.getMessage() + "] [hashCode: " + sourceChannel.socket().hashCode() + ", remotePort:" + sourceChannel.socket().getPort() + "] at channel!");
				}
				try{
					sourceChannel.socket().close();
				}catch (final Throwable e1) {
//					e1ExceptionReporter.printStackTrace(e);
				}
				try{
					sourceChannel.close();
				}catch (final Exception e1) {
//					System.out.println("try channel close...");
//					e1ExceptionReporter.printStackTrace(e);
				}
				try{
					//特殊情形下，调用本行是有益的。
					key.cancel();
				}catch (final Exception e1) {
//					System.out.println("try channel key close...");
//					e1ExceptionReporter.printStackTrace(e);
				}
			}
		}
	}

	private static void toWriteBackend(final SessionConnector sc,
			final SocketChannel targetChannel, final ByteBuffer bb, 
			final boolean serverOrClientReset)
			throws ClosedChannelException {
		//转缓存，进入后备模式
		sc.appendWriteSet(targetChannel, bb, serverOrClientReset);
		if(targetChannel != null){
			//有可能因为ClientReset，而导致暂存数据，则targetChannel为null情形出现
			final SelectionKey targetkey = targetChannel.keyFor(selector);
			if(targetkey == null){
				targetChannel.register(selector, SelectionKey.OP_WRITE);
			}else{
				targetkey.interestOps(targetkey.interestOps() | SelectionKey.OP_WRITE);
			}
		}
	}

	final static Object serverNumRefreshLock = new Object();
	
	/**
	 * 因为clientResetTimer和服务器发生断线同时发生，导致对象回收异常，故加锁
	 * @param sc
	 * @param notifyMinus 如果为true，则可能进行服务总数自减并更新；否则不作自减
	 */
	public static void closePare(final SessionConnector sc, final boolean notifyMinus) {
		synchronized (sc) {
			if(L.isLogInRelay) {
				LogManager.log("closing Pare, notifyMinus : " + notifyMinus);
			}
			final boolean isMatched = sc.isMatched;
			final boolean isDirectOK = sc.isDirectOK;
			final String id = sc.getUUIDString();
			final String token = sc.token;
			if(lineOffSessionConn(sc) && notifyMinus){//notifyMinus一定要置于，因为前者(lineOffSessionConn)一定要被执行
				if(isDirectOK && L.isLogInRelay){
					LogManager.log("skip line off to database, because direct OK.");
				}
				if(isDirectOK == false && isMatched == false && id != null && token != null){
					if(L.isLogInRelay) {
						LogManager.log("line off to database : " + id);
					}
					RootServerConnector.delLineInfo(id, token, false);
				}
				minusServNum();
			}
		}
	}

	private static void minusServNum() {
		synchronized (serverNumRefreshLock) {
			if(size == 0){
//				LogManager.errToLog("server num : 0");
//				ContextManager.getThreadPool().run(new Runnable() {
//					@Override
//					public void run() {
//						RootServerConnector.sendHttpMsg(RootServerConnector.REP_Server_Num_Under_0);
//					}
//				});
				return;
			}
			serverNum.value = (--size);
			if(L.isLogInRelay) {
				LogManager.log("server num : " + serverNum.value);
			}
			ContextManager.getThreadPool().run(refreshServerNum);//不用threadPoolToken
		}
	}

	private static void closeClient(final SessionConnector sc){
		if(sc.clientKey != null){
			try{
				sc.clientKey.cancel();
			}catch (final Throwable e) {
			}
			sc.clientKey = null;
		}

		final SocketChannel c = sc.clientSide;
		if(c != null){
			try{
//				if(L.isLogInRelay) {
//					LogManager.log("close client channel : " + c.socket().getInetAddress().getHostAddress() + ":" + c.socket().getPort());
//				}
				c.socket().close();
			}catch (final Throwable e) {
			}
			try{
				c.close();
			}catch (final Throwable e) {
			}
			sc.clientSide = null;
		}
	}
	private static void closeServer(final SessionConnector sc){
		if(sc.serverKey != null){
			try{
				sc.serverKey.cancel();
			}catch (final Throwable e) {
			}
			sc.serverKey = null;
		}
		
		final SocketChannel c = sc.serverSide;
		if(c != null){
			try{
//				if(L.isLogInRelay) {
//					LogManager.log("close server channel : " + c.socket().getInetAddress().getHostAddress() + ":" + c.socket().getPort());
//				}
				c.socket().close();
			}catch (final Throwable e) {
			}
			
			try{
				c.close();
			}catch (final Throwable e) {
			}
			sc.serverSide = null;
		}
	}
	
	/**
	 * 由于同步需要，本方法只能在closePare中使用
	 * @param sc
	 * @return
	 */
	private static boolean lineOffSessionConn(final SessionConnector sc) {
		if(sc == null){
			return false;
		}
		
		sc.isFreeStatus = true;
		
		String desc = "server,";
		
		boolean isCycle = false;
		if(sc.serverSide != null){
			desc += sc.serverSide.socket().getInetAddress().getHostAddress();
		}else{
			desc += "null";
		}
		closeServer(sc);
		
		desc += "; client,";
		
		if(sc.clientSide != null){
			desc += sc.clientSide.socket().getInetAddress().getHostAddress();
		}else{
			desc += "null";
		}
		closeClient(sc);
		
		if(sc.uuidbs != null){
			//因为有可能前置逻辑已关闭ServerOrClient，所以此处去掉条件isClose
			
			//回收后sc.uuidbs置空，所以采用本条件来防止重复回收
			if(L.isLogInRelay) {
				LogManager.log("close session pare : " + desc);
			}
			
			final int uuidlen = sc.uuidbs.len;
			
			synchronized (tdn) {
				if(sc.isDelTDN == false){
					if(L.isLogInRelay) {
						LogManager.log("remove tdn");
					}
					tdn[uuidlen].delNode(sc.uuidbs.bytes, 0, uuidlen);
				}
			}
			
			byteCache.cycle(sc.uuidbs);
	
			sc.reset();

			if(L.isLogInRelay) {
				LogManager.log("recycle SessionConnector hashcode : " + sc.hashCode());
			}
			scCacher.push(sc);
			
			isCycle = true;
		}
		
		return isCycle;
	}
	public static ExtReplayBiz erb;
	private static final DataReg dr = new DataReg();
	public static final IOException IOE = new IOException();
	private static long receiveMoveRelayStartTime;
	private static final IWatcher overrideSameTokenWatcher = new IWatcher() {
		@Override
		public boolean watch() {
			RootServerConnector.notifyHttpError(RootServerConnector.LOFF_OverrideSameToken);
			return true;
		}
		
		@Override
		public void setPara(final Object p) {
		}
		
		@Override
		public boolean isCancelable() {
			return false;
		}
		
		@Override
		public void cancel() {
		}
	};
	public static boolean pushMap(final IContext ctx, final SelectionKey key) throws Exception{
		buffer.clear();
		final byte[] bs = buffer.array();
		buffer.limit(MsgBuilder.MIN_LEN_MSG);
		final SocketChannel channel = (SocketChannel)key.channel();
		final int headReadLen = channel.read(buffer);
		if(headReadLen != MsgBuilder.MIN_LEN_MSG){
			if(headReadLen == -1){// || headReadLen == 0
				if(L.isLogInRelay) {
					LogManager.log("Unknow head data reg. readed:" + headReadLen + 
						", expect:" + MsgBuilder.MIN_LEN_MSG + ", at channel : " 
						+ channel.socket().getInetAddress().getHostAddress());
				}
				throw IOE;
			}else{
				continueRead(buffer, channel, headReadLen, MsgBuilder.MIN_LEN_MSG);
			}
		}

		boolean isNewSession = false;
		
		final byte ctrlTag = bs[MsgBuilder.INDEX_CTRL_TAG];
		if(ctrlTag == MsgBuilder.E_TAG_RELAY_REG){
			final byte firstOrReset = bs[MsgBuilder.INDEX_CTRL_SUB_TAG];
			final int dataLen = HCMessage.getMsgLen(bs);
			final int totalLen = MsgBuilder.MIN_LEN_MSG + dataLen;
			buffer.limit(totalLen);
			
			final int bodyReadLen = channel.read(buffer);
			if(bodyReadLen != dataLen){
				if(bodyReadLen == -1){
					if(L.isLogInRelay) {
						LogManager.log("Unknow data reg, readed:" + bodyReadLen + 
							", expect:" + dataLen + ", at channel : " 
							+ channel.socket().getInetAddress().getHostAddress() + ":" + channel.socket().getPort());
					}
					throw IOE;
				}else{
					continueRead(buffer, channel, bodyReadLen, dataLen);
				}
			}
			
			dr.setBytes(bs);
			final boolean isServerOnRelay = 
					(dr.getFromServer() == MsgBuilder.DATA_IS_SERVER_TO_RELAY);
			final int len = dr.getUUIDLen();
			final int uuidIndex = DataReg.uuid_index;
			final int endIdx = uuidIndex + len;
			SessionConnector sc;
			
//			if(L.isLogInRelay){
//				LogManager.log("" + firstOrReset);
//				throw BBIOException;
//			}
			
			synchronized (tdn) {
				sc = tdn[len].getNodeData(bs, uuidIndex, endIdx);
				if(sc != null){
					if(isServerOnRelay){//服务端
						final String newToken = dr.getTokenDataOut();
						if(newToken.equals(sc.token)){//上次的连接仍alive，待接入者进行等待
							if(L.isLogInRelay){
								LogManager.errToLog("the same token account seem alive, override, token [" + newToken + "].");
							}
							
							sc = overrideSameToken(sc, bs, len);
							
							GlobalConditionWatcher.addWatcher(overrideSameTokenWatcher);
						}else if(firstOrReset == MsgBuilder.DATA_E_TAG_RELAY_REG_SUB_FIRST
								|| firstOrReset == MsgBuilder.DATA_E_TAG_RELAY_REG_SUB_BUILD_NEW_CONN){
							final String uuidString = sc.getUUIDString();
							final boolean isRegedToken = RootServerConnector.isRegedToken(uuidString, newToken);
							if(isRegedToken){
								//注册级认证Token，强制关闭旧的。
								if(L.isLogInRelay) {
									LogManager.log("Override token id[" + uuidString + "] for reged " 
									+ newToken + ", at channel : " 
									+ channel.socket().getInetAddress().getHostAddress());
								}
								sc = overrideSameToken(sc, bs, len);
							}else{
								if(L.isLogInRelay) {
									LogManager.log("dirty new connection (token:"+newToken+") " +
										"on same exists token session, force close! at channel : " 
										+ channel.socket().getInetAddress().getHostAddress());
								}
								
								setRootBufferSubTag(MsgBuilder.DATA_ROOT_SAME_ID_IS_USING);
								channel.write(rootBuffer);
								
								//干扰连接型，强制关闭
								throw BBIOException;
							}
						}
					}else{//客户端
						if(L.isLogInRelay) {
							LogManager.log("found exit SessionConn");
						}
						
						//已有客户端接入
						if(sc.clientSide != null){
							//不能关闭旧的，因为本次不能证明是合法的。所以抛出异常，等待为null出现，再进入验证等
							if(L.isLogInRelay) {
								LogManager.log("try override exist client id[" + sc.getUUIDString() + "] at channel : " 
									+ channel.socket().getInetAddress().getHostAddress() + ", close try connection");
							}
							
							setRootBufferSubTag(MsgBuilder.DATA_ROOT_SAME_ID_IS_USING);
							channel.write(rootBuffer);
							
							throw BBIOException;
						}
						
//						processUDP();
						
						setRootBufferSubTag(MsgBuilder.DATA_ROOT_MATCHED_FOR_CLIENT_ON_RELAY);
						final int matchLen = sc.serverSide.write(rootBuffer);
						if(L.isLogInRelay){
							LogManager.log("====> write DATA_ROOT_MATCHED_FOR_CLIENT_ON_RELAY, len : " + matchLen);
						}
						
						if(L.isLogInRelay) {
							LogManager.log("remove tdn");
						}
						sc.isMatched = true;
						sc.isDelTDN = true;
						tdn[len].delNode(bs, uuidIndex, endIdx);
						minusServNum();
					}
				}else{//sc == null
					if(isServerOnRelay == false){ //Mobi客户端 且 服务器没上线
						//因为下面检查并发回错误状态，所以此处注释|| (sc.fromClient != null))//原旧客户连接仍保持，则新占客户端非法
						//不回应，以表明此连接为非法。
						if(L.isLogInRelay) {
							LogManager.log("Error client reg status(No server nor exist old client), NO write echo back to cancel this channel.");
						}
						throw BBIOException;
					}
					
					if(L.isLogInRelay) {
						LogManager.log("create new SessionConn");
					}
					isNewSession = true;
					sc = buildSessionConn(bs, len);
				}
			}
			
			//回应发送端，触发发送端收到此消息。
			//要先write，然后再建立SessionConnector，因为如果write失败，则不用回收SessionConnector
			buffer.flip();
			if(channel.write(buffer) != (MsgBuilder.MIN_LEN_MSG + dataLen)){
				//不能使用headReadLen,bodyReadLen,因为极端网络情形下，
				if(L.isLogInRelay) {
					LogManager.log("Reg Error, Read More, Write back less at channel : " 
							+ channel.socket().getInetAddress().getHostAddress());
				}
				throw BBIOException;
			}
			
			if(L.isLogInRelay) {
				LogManager.log("open Pare " + sc.hashCode());
			}
			sc.setKey(channel, key, isServerOnRelay);
			key.attach(sc);
			
			if(isNewSession){
				if(L.isLogInRelay) {
					LogManager.log("New Reg");
				}
			}else{
				if(L.isLogInRelay) {
					LogManager.log("Find Reg Pair");
				}
			}
			if(L.isLogInRelay) {
				LogManager.log("Reg " + (isServerOnRelay?"S":"C") + " on channel :[" + 
					channel.socket().getInetAddress().getHostAddress() + 
					":" + channel.socket().getPort() + "]");
			}
			return isNewSession;
		}else if(ctrlTag == MsgBuilder.E_TAG_NOTIFY_TO_NEW_RELAY){
			//回应以表明在线
			receiveMoveRelayStartTime = System.currentTimeMillis();
			buffer.flip();
			if(channel.write(buffer) != MsgBuilder.MIN_LEN_MSG){//不能使用headReadLen,因为极端网络情形下，
				if(L.isLogInRelay) {
					LogManager.log("E_TAG_NOTIFY_TO_NEW_RELAY write less at channel : " 
							+ channel.socket().getInetAddress().getHostAddress());
				}
				throw BBIOException;
			}
		}else{
			if(erb != null){
				if(erb.doExt(key, channel, buffer) == false){
					if(L.isLogInRelay) {
						LogManager.log("skip unforward packet.");// force Exception and close channel.
					}
//					由于可能服务器重新连接Root后，其它线程仍有可能发送旧数据，可能会产生此异常。比如sendThundmail
//					故注释下行代码ß
//					throw BBIOException;
					//unforward(channel);
				}
			}
		}
		return false;
	}

	private static SessionConnector overrideSameToken(SessionConnector sc, final byte[] bs,
			final int len) {
		closePare(sc, true);
		sc = buildSessionConn(bs, len);
		return sc;
	}
	
	private final static void processUDP(){
//		//尝试增加快捷UDP通道
//		final UDPPair up = NIOServer.buildUDPPortPair();
//		if(up != null){
//			//允许重联
//			sc.firstServerRegMS = 0;
//			
//			if(sc.udpPair != null){
//				sc.udpPair.reset();
//			}
//			sc.udpPair = up;
//			
//			//通知服务器端使用的UDP端口和客户端的UDP端口
//			final int serverPort = up.port;
//			final int clientPort = up.target.port;
//			
//			up.isServerPort = true;
//			up.target.isServerPort = false;
//			
//			final int NOTIFY_UDP_DATA_LEN = 2 + MsgBuilder.LEN_UDP_HEADER;
//			bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_TAG_ROOT;
//			bs[MsgBuilder.INDEX_CTRL_SUB_TAG] = MsgBuilder.DATA_ROOT_UDP_PORT_NOTIFY;
//			
//			//初始化UDP Header
//			sc.buildRandomUDPHeader(bs, MsgBuilder.INDEX_MSG_DATA + 2);
//			
//			HCMessage.setMsgLen(bs, NOTIFY_UDP_DATA_LEN);
//			
//			//通知Client UDP Port
//			buffer.clear();
//			ByteUtil.integerToTwoBytes(clientPort, bs, MsgBuilder.INDEX_MSG_DATA);
//			buffer.limit(MsgBuilder.INDEX_MSG_DATA + NOTIFY_UDP_DATA_LEN);
//			channel.write(buffer);
////			ContextManager.getContextInstance().send(channel.socket().getOutputStream(), notifyBS, 0, NOTIFY_UDP_DATA_LEN);
//			if(L.isLogInRelay) {
//				LogManager.log("UDP port : " + clientPort + " for client");
//			}
//			
//			//通知Server UDP Port
//			buffer.clear();
//			ByteUtil.integerToTwoBytes(serverPort, bs, MsgBuilder.INDEX_MSG_DATA);
//			buffer.limit(MsgBuilder.INDEX_MSG_DATA + NOTIFY_UDP_DATA_LEN);
//			sc.serverSide.write(buffer);
////			ContextManager.getContextInstance().send(sc.serverSide.socket().getOutputStream(), notifyBS, 0, NOTIFY_UDP_DATA_LEN);
//			if(L.isLogInRelay) {
//				LogManager.log("UDP port : " + serverPort + " for Server");
//			}
//		}
	}

	/**
	 * 由于服务器端采用代理或手机在线双类型检查，所以不需要本状态信息
	 * @param channel
	 * @throws IOException
	 */
	private static void unforward(final SocketChannel channel)
			throws IOException {
		if(L.isLogInRelay) {
			LogManager.log("unForward package reback to channel[" + channel.hashCode() + "].");
		}
		
		//未被转发，交由服务器或客户端进行自行判断，或断线重连
		unForwarBuffer.clear();
		unForwarBuffer.limit(MsgBuilder.MIN_LEN_MSG);
		if(channel.write(unForwarBuffer) != MsgBuilder.MIN_LEN_MSG){
			if(L.isLogInRelay) {
				LogManager.log("unForward write less at channel : " 
						+ channel.socket().getInetAddress().getHostAddress());
			}
			throw BBIOException;
		}
	}

	public static void continueRead(final ByteBuffer buffer,
			final SocketChannel channel, int readedLen, final int totalReadLen) throws IOException {
		//数据未完全过来。
		int newRead = 0;
		int sleepCount = 0;
		do{
			try{
				Thread.sleep(100);
				if((sleepCount++) > 20){
//					if(L.isLogInRelay) {
//						LogManager.log("continueRead more sleepCount Exception");
//					}
					throw IOE;
				}
			}catch (final Exception e) {
				
			}
			newRead = channel.read(buffer);
			if(newRead == -1){
//				if(L.isLogInRelay) {
//					LogManager.log("continueRead read -1 Exception");
//				}
				throw IOE;
			}
			readedLen += newRead;
//			if(L.isLogInRelay) {
//				LogManager.log("continueRead newRead:" + newRead + ", readedLen:" + readedLen + ", TotalTryReadLen:" + totalReadLen);
//			}
		}while(readedLen != totalReadLen);
	}

	private static SessionConnector buildSessionConn(final byte[] bs,
			final int len) {
		SessionConnector sc;
		if(scCacher.size() == 0){
			sc = new SessionConnector(bufferDirectCacher);
		}else{
			sc = (SessionConnector)scCacher.pop();
		}

		if(L.isLogInRelay) {
			LogManager.log("sessionConnector hashCode : " + sc.hashCode());
		}
		
		sc.firstServerRegMS = System.currentTimeMillis();
		sc.isFreeStatus = false;
		
		final ByteArr ba = byteCache.getFree();
		ba.len = len;
		
		for (int i = 0, j = DataReg.uuid_index; i < len; i++, j++) {
			ba.bytes[i] = bs[j];
		}
		sc.uuidbs = ba;
		sc.token = dr.getTokenDataOut();
		tdn[len].addNodeData(ba.bytes, 0, ba.len, sc);
		return sc;
	}

	public static boolean startMoveNewRelay(final CoreSession coreSocketSession, final RelayShutdownWatch watch){
		if(RelayManager.findNewRelayAndMoveTo(coreSocketSession) > 0){
			GlobalConditionWatcher.addWatcher(watch);//由于供中继，所以使用Global
			return true;
		}else{
			return false;
		}
	}

	/**
	 * 启动中继迁移进程。
	 * 停机前，并发现新的Relay，如果成功，
	 * 并将现有被服务资源迁移到新的Relay上。
	 * 如果没有发现新的Relay，则通知自己下线
	 * @return 返回大于0，表示需要后继进程等待本进程完全迁移完毕，并更新状态isMoveRelayClients
	 */
	private static int findNewRelayAndMoveTo(final CoreSession coreSocketSession){
		if(isShutdowning == true){
			return 0;
		}
		
		isShutdowning = true;
		
//		RootServerConnector.delLineInfo(TokenManager.getToken(), false);//多路模式
		
		final long now = System.currentTimeMillis();
		final long diff = now - receiveMoveRelayStartTime;
		if(diff < 15000){
			//可以正在接受MoveRelay。故加时以待其完成。
			try{
				Thread.sleep(15000 - diff);
			}catch (final Exception e) {
				
			}
		}
		synchronized (tdn) {
			if(size > 0){
			}else{
				return 0;
			}
		
			if(L.isLogInRelay) {
				LogManager.log("Move to new relay servers");
			}
			
			final Vector relays = (Vector)RootServerConnector.getNewRelayServers(IConstant.getUUID(), TokenManager.getToken());
			if(relays == null){
				if(L.isLogInRelay) {
					LogManager.log("No Relay server to move, notify shut down");
				}
				notifyClientsLineOff();
			}else{
				final String slocalIP = HCURLUtil.convertIPv46(StarterParameter.homeWirelessIpPort.ip);

				final int size = relays.size();

				for (int i = 0; i < size; i++) {
					final String[] ipAndPorts = (String[])relays.elementAt(i);
					final String ip = ipAndPorts[0];
					final int port = Integer.parseInt(ipAndPorts[1]);
					
					if(StarterParameter.homeWirelessIpPort.port == port && slocalIP.equals(ip)){
					}else{
						Socket socket = null;
						try {
							//远程状态侦测，服务器进入待服状态，此状态不能关机。
							
							socket = (Socket)coreSocketSession.buildSocket(0, ip, port); 
							if(socket == null){
								continue;
							}
							
							final OutputStream os = socket.getOutputStream();
							
							final byte[] bs = new byte[MsgBuilder.MIN_LEN_MSG];
							
							bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_TAG_NOTIFY_TO_NEW_RELAY;
							
							HCMessage.setMsgLen(bs, 0);
							
							os.write(bs, 0, bs.length);
							os.flush();
							
							socket.setSoTimeout(3000);
							
							int readLen = 0;
							final InputStream is = socket.getInputStream();
							readLen = is.read(bs);
							
							if(readLen != bs.length){
								throw new Exception();
							}
							
							//开始输送
							if(L.isLogInRelay) {
								LogManager.log("Move relay clients to new relay server, " + 
									ip + ":" + port);
							}
							
							moveToNewRelay(ip, port);
							
							socket.close();
						} catch (final Exception e) {
							if(L.isInWorkshop){
								e.printStackTrace();
							}
							if(socket != null){
								try{
									socket.close();
								}catch (final Exception e１) {
								}
							}
						}
					}
				}
			}
			return 1;
		}
	}
	
	private static int moveToNewRelay(final String newRelayip, final int newRelayport){
		int moveCount = 0;
		
		final Stack stack = new Stack();
		final byte[] bs = new byte[MsgBuilder.UDP_BYTE_SIZE];
		final DataNatReqConn nat = new DataNatReqConn();
		nat.bs = bs;
		
		for (short i = 0; i < tdn.length; i++) {
			stack.removeAllElements();
			
			tdn[i].getDataSet(stack);
			
			final int sizeStack = stack.size();
			for (int j = 0; j < sizeStack; j++) {
				final SessionConnector c = (SessionConnector)stack.elementAt(j);
//				byte[] uuid2 = c.uuid;
//				int uuid_len2 = c.uuid_len;
				
//				sendReceiveRelaySC(newRIP, newRelayport, c, uuid2, uuid_len2);
				
				try{
					final SocketChannel server = c.serverSide;
					final SocketChannel client = c.clientSide;
					
//					c.fromServer = null;
//					c.fromClient = null;
					
					if(server != null){
						try{
							//先让client挂上，因为server有可能粘包发生。
							if(client != null){
								sendMoveToNewRelay(client, nat, newRelayip, newRelayport);
							}
							
							if(server != null){
								sendMoveToNewRelay(server, nat, newRelayip, newRelayport);
							}
							
							if(L.isLogInRelay) {
								LogManager.log("remove tdn");
							}
							tdn[i].delNode(c.uuidbs.bytes, 0, c.uuidbs.len);
							moveCount++;
						}catch (final Exception e) {
							ExceptionReporter.printStackTrace(e);
							
							return moveCount;
						}
						if(L.isLogInRelay) {
							LogManager.log("Notify Server to relay");
						}
					}
					
//					lineOffSessionConn(c);
				}catch (final Exception e) {
					ExceptionReporter.printStackTrace(e);
				}
			}
		}
		return moveCount;
	}

	public static void notifyClientsLineOff() {
		final Stack stack = new Stack();
		for (short i = 0; i < tdn.length; i++) {
			stack.removeAllElements();
			
			tdn[i].getDataSet(stack);
			
			final int sizeStack = stack.size();
			for (int j = 0; j < sizeStack; j++) {
				final SessionConnector c = (SessionConnector)stack.elementAt(j);
				
				try{
					if(c.serverSide != null){
						sendLineOff(c.serverSide);
					}
					
					if(c.clientSide != null){
						sendLineOff(c.clientSide);
					}
				}catch (final Exception e) {
					ExceptionReporter.printStackTrace(e);
				}
				//因为要关闭，所以无需更改此状态
//				uuid_len[i] = 0;
//				c.reset();
			}
		}
	}
	
	private static void sendMoveToNewRelay(final SocketChannel server, 
			final DataNatReqConn nat, 
			final String newRelayServerIP, final int newRelayPort) throws IOException{
		
		final int len = nat.getLength() + MsgBuilder.INDEX_MSG_DATA;

		final ByteBuffer bb = ByteBuffer.allocate(len);
		final byte[] bs = bb.array();
		
		nat.bs = bs; 
		
		nat.setLocalIP("");
		nat.setLocalPort(0);
		nat.setRemoteIP(newRelayServerIP);
		nat.setRemotePort(newRelayPort);
		
		bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_TAG_MOVE_TO_NEW_RELAY;
		HCMessage.setMsgLen(bs, nat.getLength());
		
//		if(L.isLogInRelay) {
//			LogManager.log("Move to Relay, IP:" + newRelayServerIP + ":" + newRelayPort);
//		}
		try{
			bb.clear();
			bb.limit(len);
			final int wLen = server.write(bb);
			if(L.isLogInRelay) {
				LogManager.log("write out len:" + wLen);
			}
			//不能采用Block模式代码，会产生java.nio.channels.IllegalBlockingModeException
//			OutputStream outputStream = server.socket().getOutputStream();
//			outputStream.write(bs, 0, len);//write(mbb);
//			outputStream.flush();
		}catch (final Exception e) {
			if(L.isLogInRelay) {
				LogManager.log(e.getMessage());
			}
			ExceptionReporter.printStackTrace(e);
		}
	}
	
	private static void sendLineOff(final SocketChannel sc) throws IOException{
		final int totalLen = MsgBuilder.MIN_LEN_MSG + 5;
		
		final ByteBuffer bb = ByteBuffer.allocate(totalLen);
		final byte[] bs = bb.array();
		
		bs[MsgBuilder.INDEX_CTRL_TAG] = MsgBuilder.E_LINE_OFF_EXCEPTION;
		HCMessage.setMsgBody(bs, "false");
		
		try{
			bb.clear();
			bb.limit(totalLen);
			sc.write(bb);
		}catch (final Exception e) {
			ExceptionReporter.printStackTrace(e);
		}
	}
	
}

