package com.ibm.repotools.utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorDetailsHandle;
import com.ibm.team.repository.common.TeamRepositoryException;
import com.ibm.team.repository.common.model.Contributor;

/** A representation of an RTC server from the LDAP-RTC user sync configuration file
 * 
 * @author jamsden
 *
 */
public class RTCServer {
	private JSONObject rtcServer = null;
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
		rtcServer = obj;
		ldapConnection = connection;
		this.log = log;
		rtc = new RTCUserOperations(this, log);
	}
	
	/**
	 * @return the URI of the RTC server
	 */
	public String getServerURI() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("serverURI");
	}
	
	/** The admin element in the JSON configuration file specifies the administrator that can access and do user management for the RTC server.
	 * @return the Administrator ID for the RTC server
	 */
	public String getAdmin() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("admin");
	}
	
	/**
	 * @return the administrator's password
	 */
	public String getPassword() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("password");
	}

		
	/** Synchronize the LDAP users for this RTC server
	 *  
	 * @throws TeamRepositoryException
	 * @throws NamingException
	 */
	public void syncServerUsers() throws TeamRepositoryException, NamingException {
		if (rtcServer == null) return;
		// Get all the RTC users for this server
		List<IContributor> allUsers = rtc.getUsers();
		syncUsers("JazzAdmins", allUsers);
		syncUsers("JazzUsers", allUsers);		
		syncLicenses();
		syncProjectAreas();
	}
	
	private void synUsers(String repoPermission) {
		// Get the LDAP group capturing the users who should have this repository permission
		String racfGroupDN = (String)rtcServer.get(repoPermission);
		if (racfGroupDN == null) {
			log.warn("LDAP group for "+repoPermission+" is not specified in the configuration file");
			return;
		}
		try {
			// get the users from the LDAP group
			NamingEnumeration ldapUsers = ldapConnection.getContext().getAttributes(racfGroupDN).get("racfgroupuserids").getAll();
			if (ldapUsers == null) {
				log.error("LDAP group: "+racfGroupDN+" was not found");
				return;
			}
			
			log.info("\tSyncing "+repoPermission+" users from LDAP group: "+racfGroupDN);
			
			// 
			
			while (ldapUsers.hasMoreElements()) {
				// for each LDAP user, get their updated information if any and sync with the RTC server
				String userDN = (String)ldapUsers.next();
				Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
				if (ldapUser == null) {
					log.error("LDAP user: "+userDN+" is not defined in LDAP");
					continue;
				}
				String userId = ldapUser.get("racfid").get().toString().toLowerCase();  // RACF are all upper, Jazz are usually all lower
				
				// TODO: is the user's name racfprogrammername or something else?
				Attribute rawName = ldapUser.get("racfprogrammername");
				String name =  (rawName != null)? name = rawName.get().toString(): "UNKNOWN";
				Attribute rawEmail = ldapUser.get("racfemail");
				String email = (rawEmail != null)? email = rawEmail.get().toString(): "UNKNOWN";

				// TODO: Get all the users, create a usersToArchive map, and either add, update or remove the user from the server
				IContributor contributor = rtc.addUser(userId, name, email);
				// TODO: set the user's repository privileges
			}
		} catch (NamingException e) {
			log.error("LDAP group: "+racfGroupDN+" does not exist");;
		}				
	}
	
	/** Synchronize the LDAP users with the RTC users given the RTC repository permission. 
	 *   * Adds new users defined in the corresponding LDAP group
	 *   * Updates the user name and email address of existing users if needed
	 *   * Archives users that were removed from the LDAP group
	 *   
	 * @param repoPermission RTC repository permission (JazzAdmins, JazzUsers, etc.)
	 * @param serverUsers the existing RTC users for this server
	 * @throws NamingException
	 * 
	 * TODO: Finish RTCServer.syncUsers implementation
	 * @throws TeamRepositoryException 
	 */
	private void syncUsers(String repoPermission, List<IContributor> serverUsers) throws NamingException, TeamRepositoryException {
		String racfGroupDN = (String)rtcServer.get(repoPermission);
		if (racfGroupDN == null) {
			log.warn("LDAP group for "+repoPermission+" is not specified");
			return;
		}
		try {
			// get the users from the LDAP group
			NamingEnumeration admins = ldapConnection.getContext().getAttributes(racfGroupDN).get("racfgroupuserids").getAll();
			if (admins == null) {
				log.error("LDAP group: "+racfGroupDN+" was not found");
				return;
			}
			log.info("\tSyncing "+repoPermission+" users from LDAP group: "+racfGroupDN);
			while (admins.hasMoreElements()) {
				String userDN = (String)admins.next();
				// TODO: RTCServer.syncUsers needs to sync users by repository permissions - finish this implementation
				Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
				if (ldapUser == null) {
					log.error("LDAP user: "+userDN+" is not defined in LDAP");
					continue;
				}
				String userId = ldapUser.get("racfid").get().toString().toLowerCase();  // RACF are all upper, Jazz are usually all lower
				
				// TODO: is the user's name racfprogrammername or something else?
				Attribute rawName = ldapUser.get("racfprogrammername");
				String name =  (rawName != null)? name = rawName.get().toString(): "UNKNOWN";
				Attribute rawEmail = ldapUser.get("racfemail");
				String email = (rawEmail != null)? email = rawEmail.get().toString(): "UNKNOWN";

				// TODO: Get all the users, create a usersToArchive map, and either add, update or remove the user from the server
				IContributor contributor = rtc.addUser(userId, name, email);
				// TODO: set the user's repository privileges
			}
		} catch (NamingException e) {
			log.error("LDAP group: "+racfGroupDN+" does not exist");;
		}				
	}
	
	/** Allocates client access licenses based on membership in an LDAP group
	 * 
	 * TODO: Finish the RTCServer.syncLicenses() implementation
	 */
	public void syncLicenses() {
		if (rtcServer == null) return;
		log.info("Allocating licenses");
	}
	
	/** Synchronize the project area Administrators, Members and Process Roles for this server
	 * 
	 * @throws NamingException
	 */
	public void syncProjectAreas() throws NamingException {
		if (rtcServer == null) return;
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
		if (rtcServer == null) return null;
		List<ProjectArea> projectAreas = new ArrayList<ProjectArea>();
		Iterator<JSONObject> pas = ((JSONArray)rtcServer.get("Project Areas")).iterator();
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
