package hc.server;

import hc.App;
import hc.core.ContextManager;
import hc.core.CoreSession;
import hc.core.GlobalConditionWatcher;
import hc.core.IContext;
import hc.core.IWatcher;
import hc.core.RootConfig;
import hc.core.RootServerConnector;
import hc.core.SessionManager;
import hc.core.util.LogManager;
import hc.core.util.StringUtil;
import hc.server.ui.LinkProjectStatus;
import hc.server.ui.design.Designer;
import hc.server.ui.design.J2SESession;
import hc.util.CheckSum;
import hc.util.HttpUtil;
import hc.util.IBiz;
import hc.util.MultiThreadDownloader;
import hc.util.PropertiesManager;
import hc.util.ResourceUtil;
import hc.util.SecurityDataProtector;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.HashMap;
import java.util.Properties;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingConstants;

public class JRubyInstaller {
	static MultiThreadDownloader mtd;

	private static String getInnverJRubyMD5(final String outerVersion){
		if(ResourceUtil.isAndroidServerPlatform()){
			return getInnverAndroidJRubyMD5(outerVersion);
		}
		
		final String[] versions = {"1.7.3"};
		final String[] innerMD5 = {"75a612d9ba57a61f01dcd6e3e586a34b"};
		
		for (int i = 0; i < versions.length; i++) {
			if(versions[i].equals(outerVersion)){
				return innerMD5[i];
			}
		}
		
		return innerMD5[0];
	}

	private static String getInnverJRubySHA512(final String outerVersion){
		if(ResourceUtil.isAndroidServerPlatform()){
			return getInnverAndroidJRubySHA512(outerVersion);
		}
		
		final String[] versions = {"1.7.3"};
		final String[] innerMD5 = {"7313983a8aefc933c231a7dcd7c4d23cc1bbc057cb0ba119ea36dc6d8d83fd5a747285a5fe1322b41ede9e1402f3ec302b8488c2a5b4d756e8f31c29b596ba75"};
		
		for (int i = 0; i < versions.length; i++) {
			if(versions[i].equals(outerVersion)){
				return innerMD5[i];
			}
		}
		
		return innerMD5[0];
	}

	private static String getInnverAndroidJRubyMD5(final String outerVersion){
//		RubotoCore version 1.0.5
//		1. Updated to JRuby 1.7.19
//		2. Updated to Ruboto 1.3.0
//		3. Updated to ActiveRecord 4.1.10
//		4. Updated to activerecord-jdbc-adapter 1.3.15
//		5. Added thread_safe gem
		final String[] versions = {"1.7.19"};
		final String[] innerMD5 = {"999424ce2b20b8b1b2de0ffbbdda25e1"};
		
		for (int i = 0; i < versions.length; i++) {
			if(versions[i].equals(outerVersion)){
				return innerMD5[i];
			}
		}
		
		return innerMD5[0];
	}
	
	private static String getInnverAndroidJRubySHA512(final String outerVersion){
//		RubotoCore version 1.0.5
//		1. Updated to JRuby 1.7.19
//		2. Updated to Ruboto 1.3.0
//		3. Updated to ActiveRecord 4.1.10
//		4. Updated to activerecord-jdbc-adapter 1.3.15
//		5. Added thread_safe gem
		final String[] versions = {"1.7.19"};
		final String[] innerMD5 = {"7376ef7e5466a1afb399615b3f6117818f36e5123d1a146768b53264f357f8f32a4991c911d2f929ff2706ec997669abc7aa5ae2dd00812d4516836a52a957b7"};
		
		for (int i = 0; i < versions.length; i++) {
			if(versions[i].equals(outerVersion)){
				return innerMD5[i];
			}
		}
		
		return innerMD5[0];
	}
	public static boolean checkInstalledJRuby(){
		return PropertiesManager.getValue(PropertiesManager.p_jrubyJarVer) != null;
	}
	
	public static void startInstall(){
		if(LinkProjectStatus.tryEnterStatus(null, LinkProjectStatus.MANAGER_JRUBY_INSTALL)){
			new Thread(){
				@Override
				public void run(){
					callDownload(false);
				}
			}.start();
		}
	}
	
	public static void callDownload(final boolean isRedownload){
		if(isRedownload){
			LogManager.log("fail on download and retry download JRuby engine...");
		}else{
			LogManager.log("download JRuby engine...");
		}
		String jruby_ver = "jruby.ver";
		String jruby_md5 = "jruby.md5";
		String jruby_urls = "jruby.url";
		
		if(ResourceUtil.isAndroidServerPlatform()){
			jruby_ver = "android." + jruby_ver;
			jruby_md5 = "android." + jruby_md5;
			jruby_urls = "android." + jruby_urls;
		}

		if(isRedownload == false){
			GlobalConditionWatcher.addWatcher(new IWatcher() {
				final long ms = System.currentTimeMillis();
				@Override
				public boolean watch() {
					final CoreSession coreSS = SessionManager.getPreparedSocketSession();//注意：只需取一个即可，无需all
					if(System.currentTimeMillis() - ms > 5000 || (coreSS != null && coreSS.context != null && coreSS.context.cmStatus == ContextManager.STATUS_READY_TO_LINE_ON)){
						TrayMenuUtil.displayMessage(
								ResourceUtil.getInfoI18N(), 
								(String)ResourceUtil.get(9066), 
								IContext.INFO, null, 0);
						return true;
					}
					return false;
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
			});
		}

		final Properties thirdlibs = ResourceUtil.loadThirdLibs();
		if(thirdlibs == null){
			redownload();
			return;
		}else{
			LogManager.log("success get download online lib information.");
		}

		final String _lastJrubyVer = thirdlibs.getProperty(jruby_ver);
		final String _lastJrubyMd5 = thirdlibs.getProperty(jruby_md5);//停用

		final String md5 = getInnverJRubyMD5(_lastJrubyVer);
		final String sha512 = getInnverJRubySHA512(_lastJrubyVer);
		final CheckSum checkSum = new CheckSum(md5, sha512);
		
		String fromURL = thirdlibs.getProperty(jruby_urls);
		if(PropertiesManager.isSimu()){
			fromURL = HttpUtil.replaceSimuURL(fromURL, true);
		}
		final String storeFile = J2SEContext.jrubyjarname;
		final File rubyjar = new File(ResourceUtil.getBaseDir(), storeFile);
		if(rubyjar.exists()){
			LogManager.log("remove fail download or old version file [" + storeFile + "].");
			rubyjar.delete();
		}
		
		final IBiz biz = new IBiz() {
			@Override
			public void start() {
				PropertiesManager.setValue(PropertiesManager.p_jrubyJarFile, J2SEContext.jrubyjarname);	
				PropertiesManager.setValue(PropertiesManager.p_jrubyJarVer, _lastJrubyVer);
				PropertiesManager.saveFile();
				
				try{
					LogManager.log("successful installed JRuby.");
					
					RootServerConnector.notifyLineOffType(J2SESession.NULL_J2SESESSION_FOR_PROJECT, "lof=jrubyOK");
					
					SecurityDataProtector.init();//Android环境下进行数据加密
					
					notifySuccessInstalled();
					
					Designer.setupMyFirstAndApply();
				}finally{
					LinkProjectStatus.exitStatus();		
					closeProgressWindow();
				}
			}
			
			@Override
			public void setMap(final HashMap map) {
			}
		};
		final IBiz failBiz = new IBiz() {
			@Override
			public void start() {
				redownload();
			}
			
			@Override
			public void setMap(final HashMap map) {
			}
		};
		mtd = new MultiThreadDownloader();

		refreshProgressWindow();

		mtd.download(StringUtil.split(fromURL, RootConfig.CFG_SPLIT), rubyjar, checkSum, biz, failBiz, false, true);
	}

	private static void redownload() {
		try{
			LogManager.log("fail to get download online lib information, wait for a moment...");
			Thread.sleep(5000);
			callDownload(true);
		}catch (final Exception e) {
		}
	}
	
	public static JProgressBar getFinishPercent(){
		while(mtd == null){
			try{
				Thread.sleep(100);
			}catch (final Exception e) {
			}
		}
		return mtd.getFinishPercent();
	}
	
	private static void notifySuccessInstalled(){
		TrayMenuUtil.displayMessage(
				ResourceUtil.getInfoI18N(),  (String)ResourceUtil.get(9108), 
				IContext.INFO, null, 0);
		
		if(ResourceUtil.isEnableDesigner()){
			ContextManager.getThreadPool().run(new Runnable() {
				@Override
				public void run() {
					try{
						Thread.sleep(5 * 1000);//等待完全退出工程锁定状态。
					}catch (final Exception e) {
					}
					
					//开始第一个Har
					final JPanel panel = new JPanel(new BorderLayout());
					final String notRestart = (String)ResourceUtil.get(9156);
					final String firstHAR = (String)ResourceUtil.get(9079);
					panel.add(new JLabel("<html>" + firstHAR  + "<BR><BR>" + notRestart + "</html>", 
								App.getSysIcon(App.SYS_QUES_ICON), SwingConstants.LEADING), BorderLayout.CENTER);
					
					App.showCenterPanelMain(panel, 0, 0, ResourceUtil.getInfoI18N(), true, null, null, new HCActionListener(new Runnable() {
						@Override
						public void run() {
							LinkMenuManager.startDesigner(true);
						}
					}, App.getThreadPoolToken()), null, null, false, true, null, false, false);
				}
			});
		}
	}
	
	public synchronized static void closeProgressWindow(){
		final Window snapWindow = progressWindow;
		if(snapWindow != null){
			snapWindow.dispose();
			progressWindow = null;
		}
	}
	
	public synchronized static void refreshProgressWindow(){
		final Window snapWindow = progressWindow;
		if(snapWindow != null && snapWindow.isVisible()){
			final Container c = snapWindow.getParent();
			snapWindow.dispose();
			progressWindow = null;

			showProgressWindow(((c != null && c instanceof JFrame)?(JFrame)c:null));
		}
	}
	
	private static Window progressWindow;
	
	public synchronized static void showProgressWindow(final JFrame parent) {
			if(progressWindow == null || (progressWindow.isVisible() == false)){
				final JLabel label = new JLabel("<html>" + (String)ResourceUtil.get(9084) +
		//							"<br>if we have finished, a notify window will display." +
						"</html>", App.getSysIcon(App.SYS_INFO_ICON), SwingConstants.LEADING);
				final JPanel panel = new JPanel(new BorderLayout());
				panel.add(label, BorderLayout.CENTER);
				final JProgressBar finishPercent = getFinishPercent();
				if(finishPercent != null){
					panel.add(finishPercent, BorderLayout.SOUTH);
				}
				
				final ActionListener listener = new HCActionListener(new Runnable() {
					@Override
					public void run() {
						closeProgressWindow();
					}
				});
				
				progressWindow = App.showCenterPanelMain(panel, 0, 0, ResourceUtil.getInfoI18N(), 
						false, null, null, listener, listener, parent, true, true, null, false, false);
			}else{
				progressWindow.toFront();
			}
		}
}
