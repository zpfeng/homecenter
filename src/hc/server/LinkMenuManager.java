package hc.server;

import hc.App;
import hc.core.ContextManager;
import hc.core.util.ExceptionReporter;
import hc.server.ui.LinkProjectStatus;
import hc.server.ui.SimuMobile;
import hc.server.ui.design.Designer;
import hc.server.ui.design.code.J2SEDocHelper;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

public class LinkMenuManager {
	/**
	 * 重要，请勿在Event线程中调用，
	 * @return
	 */
	public static boolean notifyCloseDesigner(){
		return Designer.notifyCloseDesigner();
//		try {
//			final Class c = getDesignerClass();
//			final Method m = c.getMethod("notifyCloseDesigner", new Class[] {});
//			return((Boolean)m.invoke(c, new Object[] {})).booleanValue();
//		} catch (final Throwable e) {
//			ExceptionReporter.printStackTrace(e);
//			App.showConfirmDialog(null, "load link panel error : " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
//		}
//		return false;
	}

	public static void showLinkPanel(final JFrame frame){
		try {
			Designer.showLinkPanel(frame, true, null);
		} catch (final Throwable e) {
			ExceptionReporter.printStackTrace(e);
			App.showConfirmDialog(null, "load link panel error : " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	public static void closeLinkPanel(){
		try {
			Designer.closeLinkPanel();
		} catch (final Throwable e) {
			ExceptionReporter.printStackTrace(e);
			App.showConfirmDialog(null, "load link panel error : " + e.toString(), "Error", JOptionPane.ERROR_MESSAGE);
		}
	}
	
	/**
	 * 重要，请勿在Event线程中调用，
	 * @param loadInit
	 */
	public static void startDesigner(final boolean loadInit){
		SimuMobile.init();
		
		if(LinkProjectStatus.tryEnterStatus(null, LinkProjectStatus.MANAGER_DESIGN)){
			if(J2SEDocHelper.isBuildIn() == false){
				if(J2SEDocHelper.isJ2SEDocReady() == false) {
					ContextManager.getThreadPool().run(new Runnable() {
						@Override
						public void run() {
							J2SEDocHelper.checkJ2SEDocAndDownload();
						}
					});
				}
			}
			
			try{
				SingleJFrame.showJFrame(Designer.class);
				Designer.getInstance().loadInitProject(loadInit);
			}catch (final Exception e) {
				ExceptionReporter.printStackTrace(e);
				ContextManager.getThreadPool().run(new Runnable() {
					@Override
					public void run() {
						App.showConfirmDialog(null, "Cant load Designer", 
								"Error", JOptionPane.CLOSED_OPTION, JOptionPane.ERROR_MESSAGE);
					}
				});
			}
		}
	}

}
