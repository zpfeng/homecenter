package hc.server.msb;

import hc.core.util.ExceptionReporter;

import java.util.ArrayDeque;

class ProcessorRunnable extends StartableRunnable{
	final Processor processor;
	
	ProcessorRunnable(final Processor p){
		this.processor = p;
	}
	
	@Override
	public final void start(){
		try{
			processor.__startup();//in user thread group
		}catch (final Throwable e) {
			ExceptionReporter.printStackTrace(e);
			new MSBException(e.toString(), e, null, processor);
		}
		
		super.start();
	}
	
	@Override
	public final void runAfterStart(){
		
		final Workbench r_workbench = processor.workbench;
		final ArrayDeque<Message> r_todo = processor.todo;
		
		Message msg = null;
		while (true) {
			synchronized (r_todo) {
				if(processor.isShutdown){
					//清空队列
					while((msg = r_todo.pollFirst()) != null){
						msg.tryRecycle(r_workbench, true);
					}
					break;
				}
				
				msg = r_todo.pollFirst();
				if (msg == null) {
					try {
						r_todo.wait();
					} catch (final Throwable t) {
					}
					continue;
				}
			}
			
			try{
				processor.preprocess(msg);//in user thread group
//				workbench.V = workbench.O ? false : workbench.log("finish process message " + msg.toString() + "\n in processor [" + toString() + "]");
			}catch (final Throwable e) {
				ExceptionReporter.printStackTrace(e);
			}
			
			msg.tryRecycle(r_workbench, false);
		}//while
		
		try{
			processor.__shutdown();//in user thread group
		}catch (final Throwable e) {
			new MSBException("[shutdown] " + e.toString(), e, null, processor);
			ExceptionReporter.printStackTrace(e);
		}
	}
}
