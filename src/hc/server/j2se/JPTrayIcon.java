package hc.server.j2se;

import hc.App;
import hc.ITrayIcon;
import hc.PlatformTrayIcon;
import hc.WindowTrayIcon;
import hc.core.ContextManager;
import hc.server.util.HCJFrame;
import hc.util.ResourceUtil;
import hc.util.UILang;

import java.awt.ComponentOrientation;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.TrayIcon.MessageType;
import java.awt.Window;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.util.HashMap;
import java.util.Iterator;

import javax.swing.JPopupMenu;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class JPTrayIcon implements PlatformTrayIcon{
	private final ThreadGroup threadGroupToken = App.getThreadPoolToken();
	private final HashMap<String, String> tip = new HashMap<String, String>();
	
	final ITrayIcon trayIcon;
	
	public static final String NAT_DESC = "Nat_Desc";
	public static final String PUBLIC_IP = "Pub_IP";
//	public static final String PUBLIC_PORT = "Pub_Port";
	
	private JPopupMenu menu;
	private final HCJFrame frame = new HCJFrame();
	final Runnable toDisvisibleRun = new Runnable() {
		@Override
		public void run() {
			frame.setVisible(false);						
		}
	};
	private final PopupMenuListener popupListener;
	private final MouseListener mouseListener;
	private String toolTip;
	
	{
		frame.setUndecorated(true);
		frame.setAlwaysOnTop(true);
	}

	@Override
	public void exit() {
		frame.dispose();
	}
	
	public void putTip(final String key, final String value){
		tip.put(key, value);
		
		//重算ToolTip
		String toolTip = "";
		
		final Iterator<String> it = tip.keySet().iterator();
		while(it.hasNext()){
			if(toolTip.length() > 0){
				toolTip += "\n";
			}
			toolTip += tip.get(it.next());
		}
		
		trayIcon.setToolTip(toolTip);
	}
	
	@Override
	public String getToolTip(){
		return this.toolTip;
	}
	
	@Override
	public void setToolTip(final String tooltip){
		this.toolTip = tooltip;
		trayIcon.setToolTip(tooltip);
	}
	
	@Override
	public void remove(){
		trayIcon.removeTray();
	}
	
	@Override
	public void setImage(final Image image){
		trayIcon.setImage(image);
	}
	
	@Override
	public Image getImage(){
		return trayIcon.getImage();
	}
	
	private ITrayIcon buildTrayIcon(final Image image){
//		if(ResourceUtil.isWindowsOS()){
//			return new WindowTrayIcon();
//		}else{
//			return new LinuxTrayIcon(image);
			
//			if(ResourceUtil.isLinuxRelease("fedora")){
//				return new LinuxTrayIcon(image);
////			}else if(ResourceUtil.isLinuxRelease("centos")){
////					return new WindowTrayIcon();
////			}else if(ResourceUtil.isLinuxRelease("ubuntu")){
////				return new WindowTrayIcon();
//			}else if(ResourceUtil.isMacOSX()){10.9由于提示消息不正常
//				return new LinuxTrayIcon(image);
//			}else{
//				return new WindowTrayIcon();
//			}
//		}
		return new WindowTrayIcon();
	}

	public JPTrayIcon(final Image image, final String productTip, final JPopupMenu menu) {
		trayIcon = buildTrayIcon(image);
		
		//要置于setImage之前
		trayIcon.setImageAutoSize(true);
		
		trayIcon.setImage(image);
		this.toolTip = productTip;
		trayIcon.setToolTip(productTip);
		frame.addWindowFocusListener(new WindowFocusListener() {
			@Override
			public void windowLostFocus(final WindowEvent e) {
//				LogManager.log("Dialog Lose Focus");
				App.invokeLaterUI(toDisvisibleRun);
			}
			
			@Override
			public void windowGainedFocus(final WindowEvent e) {
			}
		});
		popupListener = new TrayPopupListener(frame);
		mouseListener = new TrayMouseAdapter();
		this.setJPopupMenu(menu);
		
		setToolTip(productTip);
//		
		trayIcon.showTray();
//		Locale l = Locale.getDefault();
//		menu.applyComponentOrientation(ComponentOrientation.getOrientation(l));
	}
	
	@Override
	public void displayMessage(final String caption, final String text, final MessageType messageType){
		ContextManager.getThreadPool().run(new Runnable() {
			@Override
			public void run() {
//				at : java.lang.Object.wait(Native Method)
//				at : java.lang.Object.wait(Object.java:503)
//				at : java.awt.EventQueue.invokeAndWait(EventQueue.java:1266)
//				at : java.awt.EventQueue.invokeAndWait(EventQueue.java:1247)
//				at : javax.swing.SwingUtilities.invokeAndWait(SwingUtilities.java:1347)
//				at : hc.App.invokeAndWaitUI(App.java:1479)
//				at : hc.server.j2se.JPTrayIcon$3.run(JPTrayIcon.java:162)
//				at : hc.core.util.RecycleThread.run(RecycleThread.java:27)
//				at : java.lang.Thread.run(Thread.java:744)
				App.invokeLaterUI(new Runnable() {//注意：invokeAndWaitUI会导致上述异常，改为invokeLaterUI
					@Override
					public void run() {
						trayIcon.displayMessage(caption, text, messageType);
					}
				});
			}
		},threadGroupToken);
	}
	
	public void setDefaultActionListener(final ActionListener listen){
		trayIcon.setDefaultActionListener(listen);
	}

	/**
	 * 
	 * @return
	 */
	public final JPopupMenu getJPopupMenu() {
		return menu;
	}

	/**
	 * 
	 * @param popmenu
	 */
	private final void setJPopupMenu(final JPopupMenu popmenu) {
		if (this.menu != null) {
			this.menu.removePopupMenuListener(popupListener);
			trayIcon.removeTrayMouseListener(mouseListener);
		}
		if (popmenu != null) {
			popmenu.applyComponentOrientation(ComponentOrientation.getOrientation(UILang.getUsedLocale()));

			this.menu = popmenu;
			this.menu.addPopupMenuListener(popupListener);
			trayIcon.addTrayMouseListener(mouseListener);
		}
	}

	private final class TrayMouseAdapter extends MouseAdapter {

		private void showJPopupMenu(final MouseEvent evt) {
			App.invokeLaterUI(new Runnable() {
				@Override
				public void run() {
					final Dimension screenSize = ResourceUtil.getScreenSize();
					int loc_x = evt.getXOnScreen();
					int loc_y = evt.getYOnScreen();
					
					frame.setLocation(evt.getX(), evt.getY()
								- menu.getPreferredSize().height);
					frame.setVisible(true);
					menu.show(frame.getContentPane(), 0, 0);
					// popup works only for focused windows

					if (menu.getWidth() == 0) {
						menu.setVisible(true);
						menu.setVisible(false);
					}

					final int w = menu.getWidth();
					final int h = menu.getHeight();

					if (loc_x + w > screenSize.width) {
						loc_x = loc_x - w;
					}

					if(loc_y + h > screenSize.height){
						loc_y = loc_y - h;
					}
					menu.setLocation(loc_x, loc_y);
					menu.setVisible(true);
					menu.setInvoker(frame.getContentPane());

					frame.toFront();
				}
			});
		}

		@Override
		public void mousePressed(final MouseEvent evt) {
//			showJPopupMenu(evt);
		}

		@Override
		public void mouseReleased(final MouseEvent evt) {
			if (menu != null) {
				if(evt.isPopupTrigger() ||
						(evt.getButton() == MouseEvent.BUTTON3 && evt.getClickCount() == 1)){
					showJPopupMenu(evt);
				}
			}
		}

		@Override
		public void mouseClicked(final MouseEvent evt) {
//			showJPopupMenu(evt);
		}
	}

	private class TrayPopupListener implements PopupMenuListener {
		Window dialog;

		TrayPopupListener(final Window dialog) {
			this.dialog = dialog;
		}

		@Override
		public void popupMenuWillBecomeVisible(final PopupMenuEvent evt) {
			// not used
		}

		@Override
		public void popupMenuWillBecomeInvisible(final PopupMenuEvent evt) {
//			LogManager.log("popupMenuWillBecomeInvisible");
			//必须的，该逻辑是有用的。
			dialog.setVisible(false);
		}

		@Override
		public void popupMenuCanceled(final PopupMenuEvent evt) {
//			LogManager.log("popupMenuCanceled");
			//必须的，该逻辑是有用的。
			dialog.setVisible(false);
		}
	}
}
