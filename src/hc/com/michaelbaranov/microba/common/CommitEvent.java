/*
 * Microba controls http://sourceforge.net/projects/microba/
 * Copyright (c) 2005-2006, Michael Baranov
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:
 * 
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.  
 * 3. Neither the name of the MICROBA, MICHAELBARANOV.COM, MICHAEL BARANOV nor the names of its contributors may be used to endorse or promote products derived from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS 
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY 
 * AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR 
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL 
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, 
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, 
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY 
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package hc.com.michaelbaranov.microba.common;

import java.util.EventObject;

/**
 * An event used to indicate a change to a control has been commited or reverted
 * (rolled back).
 * 
 * @author Michael Baranov
 * 
 */
public class CommitEvent extends EventObject {

	private boolean commit;

	/**
	 * Constructor.
	 * 
	 * @param source
	 *            a control that fired the event
	 * @param commit
	 *            <code>true</code> to indicate commit, <code>false</code>
	 *            to indicate revert (rollback)
	 */
	public CommitEvent(Object source, boolean commit) {
		super(source);
		this.commit = commit;
	}

	/**
	 * Returns the type of the event.
	 * 
	 * @return <code>true</code> if a change has been commited to a control,
	 *         <code>false</code> otherwise.
	 */
	public boolean isCommit() {
		return commit;
	}

}
