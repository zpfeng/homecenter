package hc.server;

import hc.core.L;
import hc.core.util.ExceptionReporter;
import hc.core.util.HCURL;
import hc.core.util.HCURLUtil;
import hc.core.util.ILog;
import hc.core.util.LogManager;
import hc.server.data.screen.PNGCapturer;
import hc.server.html5.syn.MletHtmlCanvas;
import hc.server.msb.UserThreadResourceUtil;
import hc.server.ui.DialogHTMLMlet;
import hc.server.ui.DialogMlet;
import hc.server.ui.DialogParameter;
import hc.server.ui.ICanvas;
import hc.server.ui.IMletCanvas;
import hc.server.ui.Mlet;
import hc.server.ui.MletSnapCanvas;
import hc.server.ui.ProjectContext;
import hc.server.ui.ResParameter;
import hc.server.ui.ServCtrlCanvas;
import hc.server.ui.ServerUIAPIAgent;
import hc.server.ui.design.J2SESession;
import hc.server.ui.design.SessionThread;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;

public class ScreenServer {
	public static final ServCtrlCanvas searchCtrlCanvas(final J2SESession coreSS, final String screenID){
		final Enumeration e = coreSS.mobiScreenMap.elements();
		try{
			while(e.hasMoreElements()){
				final Object ele = e.nextElement();
				if(ele instanceof ServCtrlCanvas){
					final ServCtrlCanvas ctrlCanvas = (ServCtrlCanvas)ele;
					if(ctrlCanvas.getScreenID().equals(screenID)){
						return ctrlCanvas;
					}
				}
			}
		}catch (final NoSuchElementException ex) {
		}
		
		return null;
	}
	
	public static final Object searchScreen(final J2SESession coreSS, final byte[] bs, final int offset, final int len){
		final Enumeration e = coreSS.mobiScreenMap.elements();
		try{
			while(e.hasMoreElements()){
				final Object ele = e.nextElement();
				if(ScreenServer.isMatchScreen(ele, bs, offset, len)){
					return ele;
				}
			}
		}catch (final NoSuchElementException ex) {
		}
		
		return null;
	}
	
	public static void pushToTopForMlet(final J2SESession coreSS, final String screenIDLower){
		synchronized (coreSS) {
			final char[] screenIDLowerChars = screenIDLower.toCharArray();
			
			final int size = coreSS.mobiScreenMap.size();
			for (int i = 0; i < size; i++) {
				final ICanvas canvas = (ICanvas)coreSS.mobiScreenMap.elementAt(i);
				
				if(canvas instanceof IMletCanvas){
					final IMletCanvas mhtml = (IMletCanvas)canvas;
					if(mhtml.isSameScreenIDIgnoreCase(screenIDLowerChars, 0, screenIDLowerChars.length)){
						//因为仅做画面次序调整，所以要先行，
						final String[] para = {HCURL.DATA_PARA_SHIFT_SCREEN_TO_TOP_FROM_IDX, HCURL.DATA_PARA_SHIFT_SCREEN_TO_TOP_SIZE};
						final String[] values = {String.valueOf(i), String.valueOf(size)};
						HCURLUtil.sendCmd(coreSS, HCURL.DATA_CMD_SendPara, para, values);
	
						//重新关闭或启动逻辑，所以后行，
						coreSS.mobiScreenMap.removeAt(i);
	
						pauseAndPushOldScreen(coreSS);
	
						coreSS.currScreen = (ICanvas)mhtml;
						
						resumeCurrent(coreSS);
	//					LogManager.log("shift screen [" + url + "] to top. index : " + i + ", size : " + size);
						return;
					}
				}
			}
		}
	}
	
	public static void pushScreen(final J2SESession coreSS, final ICanvas screen){
		if(screen instanceof MletHtmlCanvas){
			final MletHtmlCanvas htmlCanvas = (MletHtmlCanvas)screen;
			if(htmlCanvas.mlet instanceof DialogHTMLMlet){
				if(UserThreadResourceUtil.isInServing(coreSS.context) == false){
					return;
				}
				
				try{
					screen.onStart();
				}catch (final Throwable e) {
					ExceptionReporter.printStackTrace(e);
				}
				return;
			}
		}else if(screen instanceof MletSnapCanvas){
			final MletSnapCanvas snapCanvas = (MletSnapCanvas)screen;
			if(snapCanvas.mlet instanceof DialogMlet){
				if(UserThreadResourceUtil.isInServing(coreSS.context) == false){
					return;
				}
				
				try{
					screen.onStart();
				}catch (final Throwable e) {
					ExceptionReporter.printStackTrace(e);
				}
				return;
			}
		}
		
		synchronized (coreSS) {
			pauseAndPushOldScreen(coreSS);
			
			coreSS.currScreen = screen;
			if(L.isInWorkshop){
				LogManager.log("pushScreen current [" + screen + "]");
			}
			try{
				screen.onStart();
			}catch (final Throwable e) {
				ExceptionReporter.printStackTrace(e);
			}
		}
	}
	
	private static void removeRemoteDispalyByIdx(final J2SESession coreSS){
		final int i = coreSS.mobiScreenMap.size();
		final String[] para = {HCURL.DATA_PARA_REMOVE_SCREEN_BY_IDX};
		final String[] values = {String.valueOf(i + 1)};//mobile端是入stack后显示。
		HCURLUtil.sendCmd(coreSS, HCURL.DATA_CMD_SendPara, para, values);
	}

	private static void pauseAndPushOldScreen(final J2SESession coreSS) {
		final ICanvas currScreen = coreSS.currScreen;
		
		if(currScreen != null){
			if(currScreen instanceof MletHtmlCanvas || currScreen instanceof MletSnapCanvas){
				
				//进入isAutoReleaseAfterGo
				
				if(currScreen instanceof MletHtmlCanvas){
					final MletHtmlCanvas mletHtmlCanvas = (MletHtmlCanvas)currScreen;
					if(mletHtmlCanvas.mlet.isAutoReleaseAfterGo()){//isAutoReleaseAfterGo() is final，故可直接调用，不用入用户线程
						removeRemoteDispalyByIdx(coreSS);
						mletHtmlCanvas.onExit(true);
						if(L.isInWorkshop){
							LogManager.log("===>successful auto release after go : " + mletHtmlCanvas.mlet.getTarget());
						}
						return;
					}
				}else if(currScreen instanceof MletSnapCanvas){
					final MletSnapCanvas mletSnapCanvas = (MletSnapCanvas)currScreen;
					if(mletSnapCanvas.mlet.isAutoReleaseAfterGo()){//isAutoReleaseAfterGo() is final，故可直接调用，不用入用户线程
						removeRemoteDispalyByIdx(coreSS);
						mletSnapCanvas.onExit(true);
						if(L.isInWorkshop){
							LogManager.log("===>successful auto release after go : " + mletSnapCanvas.mlet.getTarget());
						}
						return;
					}
				}
			}
			
			//进入pause，并且压入stack
			try{
				currScreen.onPause();
			}catch (final Throwable e) {
			}
			coreSS.mobiScreenMap.push(currScreen);
		}
	}
	
	public static ICanvas getCurrScreen(final J2SESession coreSS){
		return coreSS.currScreen;
	}
	
	public static Mlet getCurrMlet(final J2SESession coreSS){
		final ICanvas curr = coreSS.currScreen;
		
		if(curr instanceof IMletCanvas){
			return ((IMletCanvas)curr).getMlet();
		}
		return null;
	}
	
	public static boolean isCurrScreenType(final J2SESession coreSS, final Class isClass){
		return isClass.isInstance(coreSS.currScreen);
	}
	
	public final static boolean isMatchScreen(final Object screenCap, final byte[] bs, final int offset, final int len) {
		return screenCap != null &&
				((screenCap instanceof PNGCapturer) && ((PNGCapturer)screenCap).isMatchScreenIDForCapture(bs, offset, len)
				|| 
				(screenCap instanceof IMletCanvas) && ((IMletCanvas)screenCap).isSameScreenID(bs, offset, len));
	}
	
	public static final IMletCanvas searchDialog(final J2SESession coreSS, final byte[] bs, final int offset, final int screenIDLen){
		final HashMap<Integer, ResParameter> questionOrDialogMap = coreSS.questionOrDialogMap;
		synchronized (questionOrDialogMap) {
			final Iterator<Integer> it = questionOrDialogMap.keySet().iterator();
			while(it.hasNext()){
				final ResParameter resPara = questionOrDialogMap.get(it.next());
				if(resPara != null && resPara instanceof DialogParameter){
					final DialogParameter para = (DialogParameter)resPara;
					final IMletCanvas canvas = para.getGlobalLock().mletCanvas;
					if(isMatchScreen(canvas, bs, offset, screenIDLen)){
						return canvas;
					}
				}
			}
		}
			
		return null;
	}
	
	/**
	 * 内部执行关闭当前窗口资源exit。
	 * @param coreSS
	 * @return
	 */
	public static boolean popScreen(final J2SESession coreSS){
		synchronized (coreSS) {
			if(coreSS.currScreen != null){
				try{
					LogManager.log(ILog.OP_STR + "back / exit");
					if(L.isInWorkshop){
						LogManager.log("back / exit [" + coreSS.currScreen + "]");
					}
					SessionThread.setWithCheckSecurityX(coreSS);
					coreSS.currScreen.onExit();//注意：不会直接调用Mlet或子类
				}catch (final Throwable e) {
					ExceptionReporter.printStackTrace(e);
				}finally{
					SessionThread.setWithCheckSecurityX(null);
				}
			}
			if(coreSS.mobiScreenMap.size() == 0){
				coreSS.currScreen = null;
				return false;
			}else{
				coreSS.currScreen = (ICanvas)coreSS.mobiScreenMap.pop();
				if(L.isInWorkshop){
					LogManager.log("resumeScreen [" + coreSS.currScreen + "]");
				}
				resumeCurrent(coreSS);
				return true;
			}
		}
	}

	private static void resumeCurrent(final J2SESession coreSS) {
		try{
			coreSS.currScreen.onResume();
		}catch (final Throwable e) {
			ExceptionReporter.printStackTrace(e);
		}
	}
	
	/**
	 * 增加同步锁，因为退出时，可能多线程，会导致被调用多次。从而确保只调用一次
	 * @param coreSS
	 * @return
	 */
	public static boolean emptyScreen(final J2SESession coreSS){
		synchronized (coreSS) {
			final boolean hasScreen = popScreen(coreSS);
			while(popScreen(coreSS)){
			}
			coreSS.isServingHomeScreen = false;
			return hasScreen;
		}
	}
	
	public static boolean isHomeScreenServing(final J2SESession coreSS){
		return coreSS.isServingHomeScreen;
	}
	
	/**
	 * 如果成功请求，则返回true；否则如被其它用户先占，则返回false。
	 * @param coreSS
	 * @return
	 */
	public static boolean askForHomeScreenService(final J2SESession coreSS){
		synchronized (coreSS) {
			if (coreSS.isServingHomeScreen) {
				return false;
			}
			coreSS.isServingHomeScreen = true;
			return true;
		}
	}

	public static final int J2SE_STANDARD_DPI = 96;

	public static void onStartForMlet(final J2SESession coreSS, final ProjectContext projectContext, final Mlet mlet) {
		ServerUIAPIAgent.runInSessionThreadPool(coreSS, ServerUIAPIAgent.getProjResponserMaybeNull(projectContext), new Runnable() {
			@Override
			public void run() {
				ServerUIAPIAgent.notifyStatusChangedForMlet(mlet, Mlet.STATUS_RUNNING);//in user thread
				mlet.onStart();
			}
		});
	}

	public static void onExitForMlet(final J2SESession coreSS, final ProjectContext projectContext, final Mlet mlet, final boolean isAutoReleaseAfterGo) {
		ServerUIAPIAgent.removeMletURLHistory(coreSS, projectContext.getProjectID(), mlet.getTarget());//可能自然pop，也可能autoRelease
		
		if(isAutoReleaseAfterGo){
			LogManager.log("Mlet/HTMLMlet [" + mlet.getTarget() + "] is auto released after go to other Mlet/HTMLMlet.");
		}
		
		ServerUIAPIAgent.runInSessionThreadPool(coreSS, ServerUIAPIAgent.getProjResponserMaybeNull(projectContext), new Runnable() {
			@Override
			public void run() {
				ServerUIAPIAgent.notifyStatusChangedForMlet(mlet, Mlet.STATUS_EXIT);//in user thread
				mlet.onExit();
			}
		});
	}

	public static void onPauseForMlet(final J2SESession coreSS, final ProjectContext projectContext, final Mlet mlet) {
		ServerUIAPIAgent.runInSessionThreadPool(coreSS, ServerUIAPIAgent.getProjResponserMaybeNull(projectContext), new Runnable() {
			@Override
			public void run() {
				ServerUIAPIAgent.notifyStatusChangedForMlet(mlet, Mlet.STATUS_PAUSE);//in user thread
				mlet.onPause();
			}
		});
	}

	public static void onResumeForMlet(final J2SESession coreSS, final ProjectContext projectContext, final Mlet mlet) {
		ServerUIAPIAgent.runInSessionThreadPool(coreSS, ServerUIAPIAgent.getProjResponserMaybeNull(projectContext), new Runnable() {
			@Override
			public void run() {
				ServerUIAPIAgent.notifyStatusChangedForMlet(mlet, Mlet.STATUS_RUNNING);//in user thread
				mlet.onResume();
			}
		});
	}
	
}
