/*
 *+------------------------------------------------------------------------+
 *| Licensed Materials - Property of IBM                                   |
 *| (C) Copyright IBM Corp. 2017.  All Rights Reserved.                    |
 *|                                                                        |
 *| US Government Users Restricted Rights - Use, duplication or disclosure |
 *| restricted by GSA ADP Schedule Contract with IBM Corp.                 |
 *+------------------------------------------------------------------------+
 */
package com.ibm.repotools.utilities;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.security.auth.login.LoginException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.util.ObfuscationHelper;

/** A representation of an RTC server from the LDAP-RTC user sync configuration file
 * 
 * @author jamsden
 *
 */
public class RTCServer {
	private JSONObject serverObject = null;
	private LdapConnection ldapConnection = null;
	private Logger log = null;
	private RTCUserOperations rtc = null;
	
	/** Construct the server, set its ldapConnection and connect to RTC
	 * 
	 * @param obj the JSON representation of the RTC server
	 * @param connection to access LDAP
	 * @param log a logger for messages
	 */
	public RTCServer(JSONObject obj, LdapConnection connection, Logger log) {
		serverObject = obj;
		ldapConnection = connection;
		this.log = log;
		try {
			rtc = new RTCUserOperations(this, log);
		} catch (LoginException e) {
			rtc = null;
		}
	}
	
	/**
	 * @return the URI of the RTC server
	 */
	public String getServerURI() {
		if (serverObject == null) return null;
		return (String)serverObject.get("serverURI");
	}
	
	/** The admin element in the JSON configuration file specifies the administrator that can access and do user management for the RTC server.
	 * @return the Administrator ID for the RTC server
	 */
	public String getAdmin() {
		if (serverObject == null) return null;
		return (String)serverObject.get("admin");
	}
	
	/**
	 * @return the administrator's password
	 */
	public String getPassword() {
		if (serverObject == null) return null;
		String password = "********"; // don't return a null password
		try {
			password = ObfuscationHelper.decryptString((String)serverObject.get("password"));
		} catch (UnsupportedEncodingException | GeneralSecurityException e) {
			// ignore decoding errors
		}
		return password;
	}

		
	/** Synchronize the LDAP users for this RTC server. This implementation use
	 * the existing RTC userSync implementation to properly handle the repository permissions.
	 * 
	 * Note: IContributor does not provide access to a user's repository permissions. These
	 * are only handled by the com.ibm.team.repository.common.service.IExternalUserRegistryService 
	 * synchronizeUsers() method.
	 *  
	 * @throws TeamRepositoryException
	 * @throws NamingException
	 */
	public void syncServerUsers() throws TeamRepositoryException, NamingException {
		if (serverObject == null || rtc == null) return;
		syncLicenses();
		syncProjectAreas();
	}
	
	
	/** Allocates client access licenses based on membership in an LDAP group
	 * @throws TeamRepositoryException 
	 * 
	 */
	public void syncLicenses() throws TeamRepositoryException {
		if (serverObject == null || rtc == null) return;  // no server found in the config file or couldn't login
		log.info("Assigning client access licenses for: "+getServerURI());
		
		// Collect the desired licenses for each user as specified in the LDAP groups in the config file
		Map<String, List<String>> desiredLicenses = new HashMap<String, List<String>>();  // <CLA, list of users> that should have the license
		
		JSONArray licenseObjects =  (JSONArray)serverObject.get("Licenses");  // contains {CLA, LDAPGroup} mappings from the config file
		if (licenseObjects == null || licenseObjects.size() == 0) {
			log.warn("No Licenses were specified for "+getServerURI());
			return;
		}
		
		// For each License object in the the JSON config file:
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> licenses = licenseObjects.iterator();
		while (licenses.hasNext()) {
			JSONObject license = licenses.next();
			if (license.keySet().size() != 1) continue; // possibly improperly defined license mapping, should be only one CLA per mapping
			
			// Note: claName is the Client Access License name (as shown in the JTS License Administration page).
			// In the JTS, licenses are identified by licenseId which is of the form com.ibm.team.rtc.developer
			// We need to carefully distinguish these two identifiers and translate between them as needed.
			// claName will be used as the client-facing name of the license.
			
			String claName = (String)license.keySet().toArray()[0];
			String racfGroupDN = (String)license.get(claName);
			// the members of this group should be assigned client access license key licenseId
			
			try {
				// For each userDN in the LDAP group and any subgroups:
				Iterator<String> ldapUsers = ldapConnection.getMembers(racfGroupDN).iterator();
				while (ldapUsers != null && ldapUsers.hasNext()) {
					String userDN = (String)ldapUsers.next();
					Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
					if (ldapUser == null) {
						log.error("LDAP user: "+userDN+" is not defined in LDAP");
						continue;
					}
					String userId = ldapUser.get("racfid").get().toString();
					// Add this userId to the list of users that should be assigned the license identified by claName
					if (desiredLicenses.containsKey(claName)) {
						desiredLicenses.get(claName).add(userId);
					} else {
						List<String> allocatedUsers = new ArrayList<String>();
						allocatedUsers.add(userId);
						desiredLicenses.put(claName, allocatedUsers);
					}

				}
			} catch (NamingException e) {
				log.error("LDAP group: "+racfGroupDN+" does not exist");;
			}
		}
		
		// Next get the users assigned to each client access license
		
		// Note: any CLA that should be allocated, e.g., Jazz Administrator needs RTC-Developer, should
		// Be defined in the groups. There's no attempt here to preserve CLAs we think 
		// should be retained, or delete users from CLAs that are not configured. This preserves
		// CLM licenses that generally should not be removed.
		
		// Note: The Jazz ADMIN will need an RTC - Developer license for this utility to run
		// The config file and LDAP groups need to be configured to ensure this license is not removed.
		
		Map<String, List<String>> actualLicenses = new HashMap<String, List<String>>();  // <CLA, list of userId>
		Iterator<String> clas = desiredLicenses.keySet().iterator();
		while (clas.hasNext()) {
			String cla = clas.next();
			IContributorHandle[] contributors = rtc.getContributorsAssignedLicense(cla);
			List<String> users = new ArrayList<String>();
				if (contributors != null) {
				for (int l=0; l<contributors.length; l++) {
					IContributor contributor = rtc.getContributor(contributors[l]);
					String userId = contributor.getUserId();
					users.add(userId);
				}
			}
			actualLicenses.put(cla, users);				
		}
		
		// Now sync the desired and actual licenses
		clas = desiredLicenses.keySet().iterator();
		while (clas.hasNext()) {
			String cla = clas.next();
			// Assume we remove them all, and take the ones we want to keep out of this list
			List<String> usersToUnassignFromlicense = actualLicenses.get(cla);
			if (desiredLicenses.get(cla) != null) {  // there are users assigned this license
				Iterator<String> desiredUsersForLicense = desiredLicenses.get(cla).iterator();
				while (desiredUsersForLicense.hasNext()) {
					String desiredUserId = desiredUsersForLicense.next();
					if (!actualLicenses.get(cla).contains(desiredUserId)) {
						// User isn't allocated the license, allocate it
						log.info("Adding client access license "+cla+" to user "+desiredUserId+" in server "+getServerURI());
						rtc.assignClientAccessLicense(cla, desiredUserId);
					} else {
						// user is already assigned the license, don't remove it
						usersToUnassignFromlicense.remove(desiredUserId);
					}
				}
			}
			// unassign the licenses the user should no longer have
			Iterator<String> usersToRemove = usersToUnassignFromlicense.iterator();
			while (usersToRemove.hasNext()) {
				String userId = usersToRemove.next();
				log.info("Unassigning client acccess license "+cla+" from user "+userId+" in server "+getServerURI());
				rtc.unassignClientAccessLicense(cla, userId);
			}
		} // End for each CLA
	}
	
	/** Synchronize the project area Administrators, Members and Process Roles for this server
	 * 
	 * @throws NamingException
	 */
	public void syncProjectAreas() throws NamingException {
		if (serverObject == null || rtc == null) return;
		Collection<ProjectArea> projectAreas = getProjectAreas();
		Iterator<ProjectArea> pas = projectAreas.iterator();
		while (pas.hasNext()) {
			ProjectArea pa = pas.next();
			pa.syncUsers();
		}
	}
	
	/**
	 * @return the RTC Project Areas configurated for this server from the configuration file.
	 */
	public Collection<ProjectArea> getProjectAreas() {
		if (serverObject == null || rtc == null) return null;
		List<ProjectArea> projectAreas = new ArrayList<ProjectArea>();
		if (serverObject.get("Project Areas") == null) return projectAreas; // none specified
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> pas = ((JSONArray)serverObject.get("Project Areas")).iterator();
		while (pas.hasNext()) {
			projectAreas.add(new ProjectArea(pas.next(), ldapConnection, rtc, log));
		}
		return projectAreas;
	}
	
	/**
	 * Disconnect from this RTC server
	 */
	public void disconnect() {
		if (rtc != null) rtc.disconnect();
	}
}
