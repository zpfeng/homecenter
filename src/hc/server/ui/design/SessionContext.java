package hc.server.ui.design;

import hc.core.L;
import hc.core.util.LogManager;
import hc.core.util.RecycleRes;
import hc.core.util.RecycleThread;
import hc.core.util.ThreadPool;
import hc.server.ui.ClientSession;
import hc.server.ui.ClientSessionForSys;

import java.util.HashMap;
import java.util.Stack;

public class SessionContext {
	final static HashMap<String, Stack<SessionContext>> map = new HashMap<String, Stack<SessionContext>>(12);
	
	public static synchronized SessionContext getFreeMobileContext(final String projID, final ThreadGroup projectGroup,
			final ProjResponser projResp){
		final Stack<SessionContext> stack = map.get(projID);
		if(stack == null || stack.size() == 0){
			if(L.isInWorkshop){
				LogManager.log("build new instance SessionContext for project [" + projID + "].");
			}
			return new SessionContext(projectGroup, projResp);
		}else{
			if(L.isInWorkshop){
				LogManager.log("re-use a SessionContext for project [" + projID + "].");
			}
			return stack.pop();
		}
	}
	
	public static synchronized void cycle(final String projID, final SessionContext mc){
		if(mc == null){
			return;
		}
		
		Stack<SessionContext> stack = map.get(projID);
		if(stack == null){
			stack = new Stack<SessionContext>();
			map.put(projID, stack);
		}
		
		stack.push(mc);
	}
	
	static int groupIdx = 1;
	
	final ThreadGroup mtg;
	public final RecycleRes recycleRes;
	private ClientSession clientSession;
	private ClientSessionForSys clientSessionForSys;
	public J2SESession j2seSocketSession;
	
	/**
	 * 由于重用，需后期更新对应值
	 * @param ss
	 * @param cs
	 */
	public final void setClientSession(final J2SESession ss, final ClientSession cs, final ClientSessionForSys csForSys){
		this.j2seSocketSession = ss;
		this.clientSession = cs;
		this.clientSessionForSys = csForSys;
	}
	
	public final ClientSession getClientSession(){
		return this.clientSession;
	}
	
	public final ClientSessionForSys getClientSessionForSys(){
		return this.clientSessionForSys;
	}
	
	public SessionContext(final ThreadGroup projectGroup, final ProjResponser proResp){
		synchronized (SessionContext.class) {
			mtg = new ThreadGroup(projectGroup, "SessionThreadPoolGroup" + (groupIdx++));
		}
		
		final ThreadPool sessionPool = new ThreadPool(mtg) {
			int threadID = 1;
			
			@Override
			protected void checkAccessPool(final Object token) {
			}
			
			private final String getNextThreadID(){
				synchronized (this) {
					return mtg.getName() + "_" + (threadID++);
				}
			}
			
			@Override
			protected Thread buildThread(final RecycleThread rt) {
				return new Thread(mtg, rt, getNextThreadID());
			}
		};
		
		recycleRes = new RecycleRes("Session", sessionPool, proResp.recycleRes.sequenceWatcher);//注意：与project共享sequenceWatcher
	}
}
