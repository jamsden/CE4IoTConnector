package com.ibm.repotools.utilities;

/** Encapsulates an overall Status for application operations. 
 * 0 - implies the overall operation was successful and users do not need to examine the log
 * -1 - implies the application completed, but there were some problems and the user should examine the log to take corrective actions
 * -2 - the application could not execute
 * Anything else would be application specific.
 * 
 * @author jamsden
 *
 */
public class Status {
	private int code = 0;
	
	public Status() {
		code = 0;
	}

	public int getCode() {
		return code;
	}

	public void setCode(int code) {
		this.code = code;
	}
	
	/**
	 * A Status variable available to any operation in the application
	 */
	public static Status appStatus = new Status();
}
