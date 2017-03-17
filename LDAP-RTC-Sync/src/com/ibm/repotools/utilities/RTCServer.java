package com.ibm.repotools.utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.repository.common.TeamRepositoryException;

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
		rtc = new RTCUserOperations(this, log);
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
		return (String)serverObject.get("password");
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
		if (serverObject == null) return;
		syncLicenses();
		syncProjectAreas();
	}
	
	
	/** Allocates client access licenses based on membership in an LDAP group
	 * 
	 */
	public void syncLicenses() {
		if (serverObject == null) return;
		log.info("Assigning client access licenses for: "+getServerURI());
		
		// Collect the desired licenses for each userId as specified in the LDAP groups in the config file
		Map<String, List<String>> desireLicenses = new HashMap<String, List<String>>();
		JSONArray licenseObjects =  (JSONArray)serverObject.get("Licenses");
		if (licenseObjects == null || licenseObjects.size() == 0) {
			log.warn("No Licenses were specified for "+getServerURI());
			return;
		}
		Iterator<JSONObject> licenses = licenseObjects.iterator();
		while (licenses.hasNext()) {
			JSONObject license = licenses.next();
			if (license.keySet().size() != 1) continue; // possibly improperly defined license mapping
			String licenseId = (String)license.keySet().toArray()[0];
			String racfGroupDN = (String)license.get(licenseId);
			// the members of this group should be assigned client access license key licenseId
			try {
				NamingEnumeration ldapUsers = ldapConnection.getContext().getAttributes(racfGroupDN).get("racfgroupuserids").getAll();
				while (ldapUsers.hasMoreElements()) {
					String userDN = (String)ldapUsers.next();
					Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
					if (ldapUser == null) {
						log.error("LDAP user: "+userDN+" is not defined in LDAP");
						continue;
					}
					String userId = ldapUser.get("racfid").get().toString();
					if (desireLicenses.containsKey(userId)) {
						desireLicenses.get(userId).add(licenseId);
					} else {
						List<String> roles = new ArrayList<String>();
						roles.add(licenseId);
						desireLicenses.put(userId, roles);
					}

				}
			} catch (NamingException e) {
				log.error("LDAP group: "+racfGroupDN+" does not exist");;
			}
		}
		// Next get the client access license keys the user currently has allocated
		Map<String, List<String>> actualLicenses = new HashMap<String, List<String>>();
		Iterator<String> users = desireLicenses.keySet().iterator();
		while (users.hasNext()) {
			String user = users.next();
			actualLicenses.put(user, rtc.getAssignedClientAccessLicenses(user));
		}
		
		// Now sync the desired and actual licenses
		users = desireLicenses.keySet().iterator();
		while (users.hasNext()) {
			String user = users.next();
			List<String> licensesToRemoveForUser = actualLicenses.get(user);
			Iterator<String> desiredLicensesForUser = desireLicenses.get(user).iterator();
			while (desiredLicensesForUser.hasNext()) {
				String desiredLicense = desiredLicensesForUser.next();
				String desiredLicenseId = rtc.getLicenseId(desiredLicense);
				if (!actualLicenses.get(user).contains(desiredLicenseId)) {
					// User doesn't play the desired role, add it
					log.info("Adding client access license "+desiredLicense+" to user "+user+" in server "+getServerURI());
					rtc.assignClientAccessLicense(desiredLicenseId, user);
				} else {
					// user is already assigned the license, don't remove it
					licensesToRemoveForUser.remove(desiredLicenseId);
				}
			}
			// unassign the licenses the user should no longer have
			Iterator<String> licensesToRemove = licensesToRemoveForUser.iterator();
			while (licensesToRemove.hasNext()) {
				String licenseId = licensesToRemove.next();
				log.info("Unassigning client acccess license "+licenseId+" from user "+user+" in server "+getServerURI());
				rtc.unassignClientAccessLicense(licenseId, user);
			}
		}
	}
	
	/** Synchronize the project area Administrators, Members and Process Roles for this server
	 * 
	 * @throws NamingException
	 */
	public void syncProjectAreas() throws NamingException {
		if (serverObject == null) return;
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
		if (serverObject == null) return null;
		List<ProjectArea> projectAreas = new ArrayList<ProjectArea>();
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
		rtc.disconnect();
	}
}
