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
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IRole;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;


/** Represents a Project Area specified in the LDAP-RTC configuration file.
 * 
 * Project Areas have Administrators, Members and ProcessRoles.
 * 
 * @author jamsden
 *
 */
public class ProjectArea {

	protected JSONObject rawPA = null;  // the JSON representation of the project or team area
	private LdapConnection ldapConnection = null;  // for accessing the LDAP server
	private RTCUserOperations rtc = null;  // for access the RTC server
	private Logger log = null;  // errors, warnings and information
	protected LinkedList<TeamArea> children = null;  // child team areas if any
	

	/** Construct a representation of a "Project Areas:" or "Team Areas:" instance from the configuration file.
	 * 
	 * @param rawPA the raw JSON representation of the project area from the configuration file
	 * @param ldapConnection used to access the LDAP server
	 * @param rtc used to access RTC
	 * @param log for logging errors, warnings and information
	 */
	public ProjectArea (JSONObject pa, LdapConnection ldapConnection, RTCUserOperations rtc, Logger log) {
		this.rawPA = pa;
		this.ldapConnection = ldapConnection;
		this.rtc = rtc;
		this.log = log;
		
		children = new LinkedList<TeamArea>();
		
		JSONArray rawTeamAreas = (JSONArray)pa.get("Team Areas");
		if (rawTeamAreas == null) return; // there are no child team areas
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> tas = rawTeamAreas.iterator();
		while (tas.hasNext()) {
			children.add(new TeamArea(this, tas.next(), ldapConnection, rtc, log));
		}
	}

	/**
	 * @return the Project Area name. 
	 */
	public String getName() {
		if (rawPA == null) return null;
		return (String)rawPA.get("name");
	}
	
	public ProjectArea getParent() {
		return null; // Project Areas are the root
	}
	
	/** Synchronize the users for this project or team area with the corresponding LDAP group.
	 *   * synchronizes the Administrators
	 *   * synchronizes the Members
	 *   * synchronizes the Process Roles
	 *   
	 * @throws NamingException
	 */
	public void syncUsers() throws NamingException {
		try {
			log.info("Syncing project or team area: "+getName());
			IProcessArea pa = rtc.getProjectArea(getName());
			if (pa == null) {
				log.error("Project or Team Area: "+getName()+" does not exist");
				Status.appStatus.setCode(-1);
				return;
			}

			IProcessItemService service = (IProcessItemService)rtc.getTeamRepository().getClientLibrary(IProcessItemService.class);
			pa = (IProcessArea)service.getMutableCopy(pa);
			
			// Administrators
			syncUsers("Administrators", pa);
			
			// Members
			syncUsers("Members", pa);

			// Process Roles
			syncProcessRoles(pa);
			
			// Save the modified project or team area
			service.save(pa, null);
			// Get a new mutable copy for the child team areas
			try {
				Thread.sleep(4000);
			} catch (InterruptedException e) {
			}
			pa = (IProcessArea)service.getMutableCopy(pa);
			
			// Now do the child Team Areas, if any
			if (children == null) return;
			Iterator<TeamArea> childTAs = children.iterator();
			while (childTAs.hasNext()) {
				childTAs.next().syncUsers();
			}
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		} catch (TeamRepositoryException e) {
			log.error("Project or Team Area: "+getName()+" exception: "+e.getMessage());
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		} catch (URISyntaxException e) {
			log.error(e.getMessage());
			Status.appStatus.setCode(-1);
		}
	}


	/** Synchronize the users who are administrators or members of this project or team area.
	 * It is assumed that the user is already a member of the JTS, and has sufficient repository permissions.
	 * This part of LDAP-RTC user synchronization is done by the repotools_syncUsers command.
	 * 
	 * @param memberRole  of a user in an RTC project area: Administrators or Members
	 * @param rawPA the RTC project or team area (an IProcessArea in the RTC SDK)
	 * 
	 * @throws NamingException
	 * @throws TeamRepositoryException 
	 */
	public void syncUsers(String memberRole, IProcessArea pa) throws NamingException, TeamRepositoryException {
		String racfGroupDN = (String)rawPA.get(memberRole);
		log.info("Syncing "+memberRole+" users from LDAP group: "+racfGroupDN);
		if (racfGroupDN == null) {
			log.warn("LDAP group for "+memberRole+" is not specified");
			Status.appStatus.setCode(-1);
			return;
		}
		try {
			// Get the LDAP Users for this project or team area
			Iterator<String> ldapUsers = ldapConnection.getMembers(racfGroupDN).iterator();

			// Get the current RTC users based on membership in the project or team area
			Map<String, IContributor> rtcMembers = rtc.getMembers(pa, memberRole);
			Map<String, IContributor> membersToRemove = new HashMap<String, IContributor>(rtcMembers);
			
			while (ldapUsers != null && ldapUsers.hasNext()) {
				String userDN = (String)ldapUsers.next();
				Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
				if (ldapUser == null) {
					log.error("LDAP user: "+userDN+" is not defined in LDAP");
					Status.appStatus.setCode(-1);
					continue;
				}
				String userId = ldapUser.get("racfid").get().toString();  
				
				Attribute rawName = ldapUser.get("racfprogrammername");
				String name =  (rawName != null)? name = rawName.get().toString(): "UNKNOWN";
				
				// Examine the RTC users, adding, updating or marking for removal is needed
				if (!rtcMembers.containsKey(userId)) {
					// Add a new user
					log.info("Adding new user: "+userId+" ("+name+") to: "+getName());
					rtc.addMember(pa, memberRole, userId);
				} else {
					membersToRemove.remove(userId); // don't remove this member
				}
				
			}
			// Remove the members that are not in the corresponding LDAP group
			Iterator<IContributor> removals = membersToRemove.values().iterator();
			while (removals.hasNext()) {
				IContributor member = removals.next();
				log.info("Removing user: "+member.getUserId()+" ("+member.getName()+"), email: "+member.getEmailAddress()+" to: "+getName());
				rtc.removeMember(pa, memberRole, member);
			}
		} catch (NamingException e) {
			log.error("LDAP group: "+racfGroupDN+" does not exist");
			Status.appStatus.setCode(-1);
		}						
	}


	
	/** Synchronizes the process roles for this project or team area. The process roles in the configuration file
	 * must match the process roles for the process description defined for the ProjectArea.
	 * 
	 * @param p the project or team area to synchronize
	 * @throws TeamRepositoryException 
	 */
	public void syncProcessRoles(IProcessArea p) throws TeamRepositoryException {
		log.info("Syncing process roles for "+getName());
		
		// Collect the desired roles for each userId as specified in the LDAP groups in the config file
		// desiredRoles<user, list of desired roles>
		Map<String, List<String>> desiredRoles = new HashMap<String, List<String>>();
		JSONArray processRoleObjects =  (JSONArray)rawPA.get("Process Roles");
		if (processRoleObjects == null || processRoleObjects.size() == 0) {
			log.warn("No process roles were specified for "+getName());
			return;
		}
		@SuppressWarnings("unchecked")
		Iterator<JSONObject> processRoles = processRoleObjects.iterator();
		while (processRoles.hasNext()) {
			JSONObject processRole = processRoles.next();
			if (processRole.keySet().size() != 1) continue; // possibly improperly defined role mapping
			String roleName = (String)processRole.keySet().toArray()[0];
			String racfGroupDN = (String)processRole.get(roleName);
			// the members of this group should be assigned role roleName
			try {
				Iterator<String> ldapUsers = ldapConnection.getMembers(racfGroupDN).iterator();
				while (ldapUsers != null && ldapUsers.hasNext()) {
					String userDN = (String)ldapUsers.next();
					Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
					if (ldapUser == null) {
						log.error("LDAP user: "+userDN+" is not defined in LDAP");
						Status.appStatus.setCode(-1);
						continue;
					}
					String userId = ldapUser.get("racfid").get().toString();
					if (desiredRoles.containsKey(userId)) {
						desiredRoles.get(userId).add(roleName);
					} else {
						List<String> roles = new ArrayList<String>();
						roles.add(roleName);
						desiredRoles.put(userId, roles);
					}

				}
			} catch (NamingException e) {
				log.error("LDAP group: "+racfGroupDN+" does not exist");
				Status.appStatus.setCode(-1);
			}
		}
		// Next get the roles the users currently play in the project area - these may be lower case
		Map<String, List<IRole>> actualRoles = new HashMap<String, List<IRole>>();
		Set<String> allUsers = rtc.getMembers(p, "Members").keySet();
		Iterator<String> users = allUsers.iterator();
		while (users.hasNext()) {
			String user = users.next();
			actualRoles.put(user, rtc.getRoleAssignments(p, user));
		}
		
		// Now sync the desired and actual roles
		users = allUsers.iterator();
		while (users.hasNext()) {
			String user = users.next();
			List<IRole> rolesToRemoveForUser = actualRoles.get(user);  // assume we remove all the roles
			List<String> desiredRolesForUser = desiredRoles.get(user);
			if (desiredRolesForUser != null) {
				Iterator<String> roles = desiredRolesForUser.iterator();
				while (roles.hasNext()) {
					String desiredRole = roles.next();
					IRole actualRole = getRole(desiredRole, actualRoles.get(user));
					if (actualRole == null) {
						// User doesn't play the desired role, add it
						log.info("Adding role "+desiredRole+" to user "+user+" in project area "+p.getName());
						rtc.addProcessRole(p, desiredRole, user);
					} else {
						// User already plays the desired role, don't remove it
						rolesToRemoveForUser.remove(actualRole);
					}
				}
			}
			// remove the roles the user should no longer play
			Iterator<IRole> rolesToRemove = rolesToRemoveForUser.iterator();
			while (rolesToRemove.hasNext()) {
				IRole role = rolesToRemove.next();
				log.info("Removing role "+role.getId()+" from user "+user+" in project area "+p.getName());
				rtc.removeProcessRole(p, role.getId(), user);
			}
		}
	}
	
	/** Find an IRole in a list given the role name
	 * @param roleName
	 * @param roleList
	 * @return The IRole mathching roleName (ignoring case) or null if there is no match
	 */
	private IRole getRole(String roleName, List<IRole>roleList) {
		Iterator<IRole> roles = roleList.iterator();
		while (roles.hasNext()) {
			IRole role = roles.next();
			if (role.getId().equalsIgnoreCase(roleName)) return role;
		}
		return null;
	}
	
}
