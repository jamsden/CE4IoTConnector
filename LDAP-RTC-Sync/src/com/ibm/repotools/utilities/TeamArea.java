package com.ibm.repotools.utilities;

import java.util.Iterator;

import org.json.simple.JSONObject;
import org.slf4j.Logger;

/** Represents a TeamArea specified in the LDAP-RTC configuration file.
 * 
 * In this case a TeamArea synchronized the same as a ProjectArea. The
 * Difference is that a TeamArea has a hierarchical name.
 * 
 * @author jamsden
 *
 */
public class TeamArea extends ProjectArea {
	
	private ProjectArea parent = null;
		
	/** Construct a representation of a "Project Areas" instance from the configuration file.
	 * 
	 * @param pa the raw JSON representation of the project area from the configuration file
	 * @param ldapConnection used to access the LDAP server
	 * @param rtc used to access RTC
	 * @param log for logging errors, warnings and information
	 */
	public TeamArea (ProjectArea parent, JSONObject pa, LdapConnection ldapConnection, RTCUserOperations rtc, Logger log) {
		super(pa, ldapConnection, rtc, log);
		this.parent = parent;
	}
	
	public ProjectArea getParent() {
		return parent; // Project Areas are the root
	}
	

	
	/**
	 * @return the Project Area name. A Team Area is a URI of the form: ProjectAreaName/TeamAreaName/TeamAreaName
	 */
	public String getName() {
		// construct the Team Area name by adding the parent Team/Project area names up the hierarchy
		String name = (String)rawPA.get("name"); // start with my name
		ProjectArea parent = getParent();
		while (parent != null) {
			name = (String)parent.rawPA.get("name") + "/" + name;
			parent = parent.getParent();
		}
		return name;
	}

}
