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
		if (rtcServer == null) return;
		syncLicenses();
		syncProjectAreas();
	}
	
	
	/** Allocates client access licenses based on membership in an LDAP group
	 * 
	 * TODO: Finish the RTCServer.syncLicenses() implementation
	 */
	public void syncLicenses() {
		if (rtcServer == null) return;
		log.info("Assigning client access licenses");
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
