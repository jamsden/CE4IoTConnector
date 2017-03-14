package com.ibm.repotools.utilities;

import org.json.simple.JSONObject;
import org.slf4j.Logger;

/** Represents a ProjectArea specified in the LDAP-RTC configuration file.
 * 
 * In this case a ProjectArea is essentially a root TeamArea managed directly by the server.
 * 
 * @author jamsden
 *
 */
public class ProjectArea extends TeamArea {
	
	private JSONObject pa = null;
	
	/** Construct a representation of a "Project Areas" instance from the configuration file.
	 * 
	 * @param pa the raw JSON representation of the project area from the configuration file
	 * @param ldapConnection used to access the LDAP server
	 * @param rtc used to access RTC
	 * @param log for logging errors, warnings and information
	 */
	public ProjectArea (JSONObject pa, LdapConnection ldapConnection, RTCUserOperations rtc, Logger log) {
		super(pa, ldapConnection, rtc, log);
	}
	
}
