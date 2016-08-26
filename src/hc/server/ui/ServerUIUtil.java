package hc.server.ui;

import hc.core.ContextManager;
import hc.core.IConstant;
import hc.core.L;
import hc.core.MsgBuilder;
import hc.core.RootServerConnector;
import hc.core.cache.CacheManager;
import hc.core.sip.SIPManager;
import hc.core.util.ByteUtil;
import hc.core.util.CCoreUtil;
import hc.core.util.ExceptionReporter;
import hc.core.util.LogManager;
import hc.core.util.StringUtil;
import hc.core.util.ThreadPool;
import hc.core.util.UIUtil;
import hc.server.ProcessingWindowManager;
import hc.server.ThirdlibManager;
import hc.server.msb.MSBAgent;
import hc.server.ui.design.MobiUIResponsor;
import hc.server.ui.design.ProjResponser;
import hc.server.util.CacheComparator;
import hc.util.BaseResponsor;
import hc.util.HttpUtil;
import hc.util.PropertiesManager;
import hc.util.ResourceUtil;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.TitledBorder;

public class ServerUIUtil {
	public static final Object LOCK = new Object();
	
	public static boolean useHARProject = PropertiesManager.isTrue(PropertiesManager.p_IsMobiMenu);
	private static BaseResponsor responsor;
	
	public static boolean isStarted(){
		return isStared;
	}
	
	public static ProjectContext buildProjectContext(final String id, final String ver,
			final ThreadPool pool, final ProjResponser projResponser,
			final ProjClassLoaderFinder finder) {
		return new ProjectContext(id, ver, pool, projResponser, finder);
	}
	
	public static BaseResponsor getResponsor(){
		CCoreUtil.checkAccess();
		
		//加锁，以确保获得必须在启动占锁之后
		synchronized (LOCK) {
			return responsor;
		}
	}
	
	/**
	 * 所有组件BorderLayout.NORTH
	 * @param components
	 * @param startIdx
	 * @param lastLayout
	 * @return
	 */
	public static JPanel buildNorthPanel(final JComponent[] components, int startIdx, final String lastLayout){
		final JPanel panel = new JPanel();
		panel.setLayout(new BorderLayout());
		panel.add(components[startIdx], BorderLayout.NORTH);
		
		if(++startIdx < components.length){
			final JPanel out = buildNorthPanel(components, startIdx, lastLayout);
			if(startIdx == components.length - 1){
				panel.add(out, lastLayout);
			}else{
				panel.add(out, BorderLayout.CENTER);
			}
		}
		
		return panel;
	}
	
	public static JPanel buildDescPanel(final String htmlWithoutHTML){
		final JPanel descPanel = new JPanel(new BorderLayout());
		descPanel.add(new JLabel("<html>" + htmlWithoutHTML + "</html>"), BorderLayout.CENTER);
		descPanel.setBorder(new TitledBorder((String)ResourceUtil.get(9095)));
		return descPanel;
	}
	
	private static boolean isStared = false;
	
	public static BaseResponsor buildMobiUIResponsorInstance(){
		CCoreUtil.checkAccess();
		
		try {
			return new MobiUIResponsor();
		}catch (final Throwable e) {
			ExceptionReporter.printStackTrace(e);
			return null;
		}
	}
	
	private static boolean isModiThirdLibs = false;
	
	public static boolean isModiThridLibs(){
		return isModiThirdLibs;
	}
	
	public static void notifyModiThridLibs(){
		CCoreUtil.checkAccess();
		
		isModiThirdLibs = true;
	}
	
	/**
	 * 
	 * @param owner 可能为null。因为MyFirst.har发布时，无此对象
	 * @param mobiUIRep
	 * @return
	 */
	public static BaseResponsor restartResponsorServer(final Frame owner, final BaseResponsor mobiUIRep){
		CCoreUtil.checkAccess();
		
		synchronized (LOCK) {
			stop();
			
			if(useHARProject){
				if(isModiThirdLibs){
					isModiThirdLibs = false;
					ThirdlibManager.loadThirdLibs();
					ResourceUtil.getJRubyClassLoader(true);
				}
				
				BaseResponsor respo = null;
				try{
					respo = (mobiUIRep != null)?mobiUIRep:(BaseResponsor)buildMobiUIResponsorInstance();
					responsor = respo.checkAndReady(owner);
				}catch (final Throwable e) {
					//出现构建失败
					ExceptionReporter.printStackTrace(e);
				}
				if(responsor == null){
					L.V = L.O ? false : LogManager.log("cancel all HAR projects.");
					cancelHAR(respo);
				}
				if(responsor == null){
					responsor = new DefaultUIResponsor();
				}
			}else{
				responsor = new DefaultUIResponsor();
			}
			try{
				CacheManager.clearBuffer();
				MSBAgent.resetDeviceSet();
				ServerUIAPIAgent.resetQuestion();
				
				responsor.start();
				
				if(responsor instanceof MobiUIResponsor){
					L.V = L.O ? false : LogManager.log("successful start all HAR projects");
					((MobiUIResponsor)responsor).preLoadJRubyScripts();
				}
			}catch (final Throwable e) {
				ExceptionReporter.printStackTrace(e);
				
				//出现启动失败
				cancelHAR(responsor);
				
				if(responsor instanceof MobiUIResponsor){
					responsor = new DefaultUIResponsor();
					responsor.start();
				}
			}
			
			isStared = true;

			return responsor;
		}
	}

	private static void cancelHAR(final BaseResponsor respo) {
		if(respo != null && respo instanceof MobiUIResponsor){
			((MobiUIResponsor)respo).release();
		
			ContextManager.getThreadPool().run(new Runnable() {
				@Override
				public void run() {
					final Window w = ProcessingWindowManager.showCenterMessage((String)ResourceUtil.get(9091));
					try{
						Thread.sleep(2000);
					}catch (final Exception e) {
					}
					w.dispose();
				}
			});
		}
	}

	public static void stop() {
		CCoreUtil.checkAccess();
		
		BaseResponsor snap;
		synchronized (LOCK) {
			snap = responsor;
			if(snap != null && isStared){
				isStared = false;
				responsor = null;
			}
		}
		
		if(snap != null){
			try{
				snap.stop();
				if(snap instanceof MobiUIResponsor){
					L.V = L.O ? false : LogManager.log("stop all projects.");
				}
			}catch (final Throwable e) {
				ExceptionReporter.printStackTrace(e);
			}
			
			if(snap instanceof MobiUIResponsor){
				try{
					final int halfSleep = ResourceUtil.getIntervalSecondsForNextStartup() * 1000 / 2;
					
					Thread.sleep(halfSleep);
					System.gc();
					Thread.sleep(halfSleep);
					System.gc();
				}catch (final Exception e) {
				}
			}
		}
	}

	public static void transMenuWithCache(final String menuData, final String projID) {
		final byte[] data = StringUtil.getBytes(menuData);
		
		final byte[] projIDbs = ByteUtil.getBytes(projID, IConstant.UTF_8);
		final String uuid = ServerUIAPIAgent.getMobileUID();
		final byte[] uuidBS = ByteUtil.getBytes(uuid, IConstant.UTF_8);
		final String urlID = CacheManager.ELE_URL_ID_MENU;
		final byte[] urlIDbs = CacheManager.ELE_URL_ID_MENU_BS;
		
		final CacheComparator menuCacheComparer = new CacheComparator(projID, uuid, urlID, projIDbs, uuidBS, urlIDbs) {
			@Override
			public void sendData(final Object[] paras) {
				response(menuData);
			}
		};
		
		menuCacheComparer.encodeGetCompare(data, 0, data.length, null);
	}
	
	public static boolean response(final String out) {
		CCoreUtil.checkAccess();
		
		ContextManager.getContextInstance().send(MsgBuilder.E_CANVAS_MAIN, out);
		return true;
	}

	/**
	 * 注意：方法必须在一个线程内同步完成
	 * @param isQuery
	 * @param parent 可能为null。因为初始发布MyFirst.har
	 * @return
	 */
	public static boolean promptAndStop(final boolean isQuery, final JFrame parent){
		CCoreUtil.checkAccess();
		
		final boolean isPrompt = isServing();
		if(isPrompt){
			HttpUtil.notifyStopServer(isQuery, parent);
			
			RootServerConnector.notifyLineOffType(RootServerConnector.LOFF_ServerReq_STR);
			SIPManager.notifyRelineon(false);
		}

		ServerUIUtil.stop();		
		
		return isPrompt;
	}

	public static boolean isServing() {
		return ContextManager.cmStatus == ContextManager.STATUS_SERVER_SELF;
	}

	public static boolean isReadyToLineOn() {
		return ContextManager.cmStatus == ContextManager.STATUS_READY_TO_LINE_ON;
	}

	public static void restartResponsorServerDelayMode(final Frame owner, final BaseResponsor mobiUIRep) {
		CCoreUtil.checkAccess();
		
		ContextManager.getThreadPool().run(new Runnable() {
			@Override
			public void run() {
				restartResponsorServer(owner, mobiUIRep);
			}
		});
	}

	public static final ProjResponser getCurrentProjResponser(){
		CCoreUtil.checkAccess();
		
		final BaseResponsor resp = getResponsor();
		if(resp != null && resp instanceof MobiUIResponsor){
			final MobiUIResponsor mobiResp = (MobiUIResponsor)resp;
			return mobiResp.getCurrentProjResponser();
		}
		return null;
	}

	/**
	 * 如果解析失败，则返回null
	 * @param base64
	 * @return
	 */
	public static BufferedImage base64ToBufferedImage(final String base64) {
		final byte[] bs = ByteUtil.decodeBase64(base64);
		
		final ByteArrayInputStream bais = new ByteArrayInputStream(bs);
		try {
			return ImageIO.read(bais);
		} catch (final IOException e) {
		}
		return null;
	}

	/**
	 * 如果解析失败，则返回null
	 * @param bi
	 * @param iconByteArrayos
	 * @return
	 */
	public static String imageToBase64(final BufferedImage bi, final HCByteArrayOutputStream iconByteArrayos) {
		final int doubleSize = bi.getHeight() * bi.getWidth() * 2;
		byte[] iconBytes = iconByteArrayos.buf;//new byte[1024 * 20];
		
		if(iconBytes == null || iconBytes.length < doubleSize){
			iconBytes = new byte[doubleSize];
			iconByteArrayos.reset(iconBytes, 0);
		}else{
			iconByteArrayos.reset();
		}
		try {
			ImageIO.write(bi, "png", iconByteArrayos);
		} catch (final Exception e1) {
			return null;
		}
		final int pngDataLen = iconByteArrayos.size();
		final byte[] data = new byte[pngDataLen];
		System.arraycopy(iconBytes, 0, data, 0, pngDataLen);
		return ByteUtil.encodeBase64(data);
	}

	public static HCByteArrayOutputStream buildForMaxIcon() {
		return new HCByteArrayOutputStream(UIUtil.ICON_MAX * UIUtil.ICON_MAX * 2);
	}

}
