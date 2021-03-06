package hc.server.msb;

import hc.server.ui.ProjectContext;
import hc.server.ui.ServerUIAPIAgent;

/**
 * {@link Converter} is useful to convert data format between {@link Robot} and {@link Device} (maybe data source for cloud), if data format exchanged between them is inconsistent.
 * <BR><BR>
 * if {@link Device} or data source is changed, you may still keep {@link Robot} intact, <BR><BR>
 * for example, {@link Robot} <i>R</i> (in HAR project <i>proj_r</i>) drive {@link Device} <i>A</i> (in HAR project <i>proj_dev_a</i>). If {@link Device} <i>A</i> is substituted by {@link Device} <i>B</i>, 
 * then do as following:<BR>
 * 1. remove HAR project <i>proj_dev_a</i> from server,<BR>
 * 2. add HAR project <i>proj_dev_b</i>,<BR>
 * 3. add HAR project <i>proj_cvt_c</i>, which {@link Converter} <i>A_to_B</i> is included in, <BR>
 * 4. bind <i>Reference Device ID</i> (in {@link Robot} <i>R</i>) to real device ID (in {@link Device} <i>B</i>) and set {@link Converter} <i>A_to_B</i> between them.<BR>
 */
public abstract class Converter {
	final ProjectContext __context;
	ConverterWrapper __fpwrapper;
	
	/**
	 * @deprecated
	 */
	@Deprecated
	Workbench __workbench;
	
	private String __name;
	final String classSimpleName;
	
	/**
	 * @deprecated
	 */
	@Deprecated
	public Converter(){
		this("");
	}
	
	/**
	 * @deprecated
	 */
	@Deprecated
	public Converter(final String name){
		this.classSimpleName = this.getClass().getSimpleName();
		__context = ProjectContext.getProjectContext();
		this.__name = ServerUIAPIAgent.getProcessorNameFromCtx(__context, name, ServerUIAPIAgent.CONVERT_NAME_PROP);
	}

	/**
	 * return the {@link ProjectContext} instance of current project.
	 * @return
	 * @since 7.0
	 */
	public ProjectContext getProjectContext(){
		return __context;
	}
	
	/**
	 * return the name of {@link Converter}
	 * @return
	 * @since 7.0
	 */
	final String getName(){
		return __name;
	}
	
	/**
	 * @param name the name of {@link Converter}
	 * @since 7.0
	 */
	final void setName(final String name){
		this.__name = name;
	}
	
	/**
	 * convert {@link Message} <code>fromDevice</code> to {@link Message} <code>toRobot</code>.
	 * <br><br>after converting, the <code>toRobot</code> will be dispatched to target, the <code>fromDevice</code> will be recycled by server.
	 * <br>it is <Strong>NOT</Strong> allowed to keep any references of {@link Message} in the instance of {@link Converter}.
	 * <br><br>to print log about creating/converting/transferring/recycling of message, please enable [Option/Developer/log MSB message].
	 * @param fromDevice {@link Message} will be converted from.
	 * @param toRobot {@link Message} will be converted to.
	 * @see #downConvert(Message, Message)
	 * @since 7.0
	 */
	public abstract void upConvert(Message fromDevice, Message toRobot);
	
	/**
	 * convert {@link Message} <code>fromRobot</code> to {@link Message} <code>toDevice</code>.
	 * <BR><BR>after converting, the <code>toDevice</code> will be dispatched to target, the <code>fromRobot</code> will be recycled by server.
	 * <BR><BR>it is <Strong>NOT</Strong> allowed to keep any references of {@link Message} in the instance of {@link Converter}.
	 * <BR><BR>to print log about creating/converting/transferring/recycling of message, please enable [Option/Developer/log MSB message].
	 * @param fromRobot the message will be auto recycled by HomeCenter server.<BR>the {@link Message} is NOT allowed to modified any parts, because this {@link Message} may be consumed by other {@link Converter}.
	 * @param toDevice the target format {@link Message}.
	 * @see #upConvert(Message, Message)
	 * @since 7.0
	 */
	public abstract void downConvert(Message fromRobot, Message toDevice);
	
	final void __forward(final Message msg) {
		__fpwrapper.__response(msg, msg.ctrl_is_downward);
	}
	
	/**
	 * return the compatible description of upside {@link Device}.
	 * @return
	 * @see #getDownDeviceCompatibleDescription()
	 * @see Robot#getDeviceCompatibleDescription(String)
	 * @since 7.0
	 */
	public abstract DeviceCompatibleDescription getUpDeviceCompatibleDescription();
	
	/**
	 * return the compatible description of downside {@link Device}.
	 * @return
	 * @see #getUpDeviceCompatibleDescription()
	 * @see Device#getDeviceCompatibleDescription()
	 * @since 7.0
	 */
	public abstract DeviceCompatibleDescription getDownDeviceCompatibleDescription();
	
	/**
	 * start up and initialize the {@link Converter}.
	 * <br><br>this method is executed by server <STRONG>before</STRONG> {@link ProjectContext#EVENT_SYS_PROJ_STARTUP}
	 * @since 7.0
	 */
	public void startup(){
	}
	
	/**
	 * shutdown the {@link Converter}
	 * <br><br>this method is executed by server <STRONG>after</STRONG> {@link ProjectContext#EVENT_SYS_PROJ_SHUTDOWN}
	 * @since 7.0
	 */
	public void shutdown() {
	}

	/**
	 * return the description of this {@link Converter}.
	 * @return
	 * @since 7.3
	 */
	public String getIoTDesc(){
		return this.classSimpleName + Processor.buildDesc(__name, __context);
	}
}
