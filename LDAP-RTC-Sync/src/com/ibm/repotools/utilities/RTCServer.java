package com.ibm.repotools.utilities;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorDetailsHandle;
import com.ibm.team.repository.common.TeamRepositoryException;

public class RTCServer {
	private JSONObject rtcServer = null;
	private LdapConnection ldapConnection = null;
	private Logger log = null;
	private RTCUserOperations rtc = null;
	
	public RTCServer(JSONObject obj, LdapConnection connection, Logger log) {
		rtcServer = obj;
		ldapConnection = connection;
		this.log = log;
		rtc = new RTCUserOperations(this, log);
	}
	
	public String getServerURI() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("serverURI");
	}
	
	public String getAdmin() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("admin");
	}
	
	public String getPassword() {
		if (rtcServer == null) return null;
		return (String)rtcServer.get("password");
	}

		
	public void syncServerUsers() throws TeamRepositoryException, NamingException {
		if (rtcServer == null) return;
		List<IContributor> allUsers = rtc.getUsers();
		Iterator<IContributor> users = allUsers.iterator();
		while (users.hasNext()) {
			IContributor user = users.next();
			IContributorDetailsHandle details = user.getDetails();
			System.out.println(user.getUserId()+" "+user.getName()+" "+user.getEmailAddress());
		}
		syncUsers("JazzAdmins", allUsers);
		syncUsers("JazzUsers", allUsers);
	}
	
	private void syncUsers(String repoPermission, List serverUsers) throws NamingException {
		String racfGroupDN = (String)rtcServer.get(repoPermission);
		if (racfGroupDN == null) {
			log.warn("LDAP group for "+repoPermission+" is not specified");
			return;
		}
		NamingEnumeration admins = ldapConnection.getContext().getAttributes(racfGroupDN).get("racfgroupuserids").getAll();
		if (admins == null) {
			log.error("LDAP group: "+racfGroupDN+" was not found");
			return;
		}
		// get the users from the RTC server based on the repository permissions
		while (admins.hasMoreElements()) {
			String userDN = (String)admins.next();
			System.out.println(userDN);
		}				
	}
	
	public void syncLicenses() {
		if (rtcServer == null) return;
	}
	
	public Collection<ProjectArea> getProjectAreas() {
		if (rtcServer == null) return null;
		List<ProjectArea> projectAreas = new ArrayList<ProjectArea>();
		Iterator pas = ((JSONArray)rtcServer.get("Project Areas")).iterator();
		while (pas.hasNext()) {
			projectAreas.add(new ProjectArea((JSONObject)pas.next()));
		}
		return projectAreas;
	}
	
	public void disconnect() {
		rtc.disconnect();
	}
}
