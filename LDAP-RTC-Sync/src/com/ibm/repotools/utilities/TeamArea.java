package com.ibm.repotools.utilities;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.slf4j.Logger;

import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.client.workingcopies.IProcessAreaWorkingCopy;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;


/** Represents a Team Area specified in the LDAP-RTC configuration file.
 * 
 * Team Areas have Administrators, Members and ProcessRoles.
 * 
 * @author jamsden
 *
 */
public class TeamArea {

	private JSONObject ta = null;  // the JSON representation of the team area
	private LdapConnection ldapConnection = null;  // for accessing the LDAP server
	private RTCUserOperations rtc = null;  // for access the RTC server
	private Logger log = null;  // errors, warnings and information
	Collection<TeamArea> children = null;  // child team areas if any
	

	/** Construct a representation of a "Team Areas" instance from the configuration file.
	 * 
	 * @param pa the raw JSON representation of the project area from the configuration file
	 * @param ldapConnection used to access the LDAP server
	 * @param rtc used to access RTC
	 * @param log for logging errors, warnings and information
	 */
	public TeamArea (JSONObject ta, LdapConnection ldapConnection, RTCUserOperations rtc, Logger log) {
		this.ta = ta;
		this.ldapConnection = ldapConnection;
		this.rtc = rtc;
		this.log = log;
		
		children = new ArrayList<TeamArea>();
		
		JSONArray rawTeamAreas = (JSONArray)ta.get("Team Areas");
		if (rawTeamAreas == null) return; // there are no team areas
		Iterator<JSONObject> tas = rawTeamAreas.iterator();
		while (tas.hasNext()) {
			children.add(new TeamArea(tas.next(), ldapConnection, rtc, log));
		}
	}

	/**
	 * @return the Team Area name
	 */
	public String getName() {
		if (ta == null) return null;
		return (String)ta.get("name");
	}
	
	/** Synchronize the users for this team area with the corresponding LDAP group.
	 *   * synchronizes the Administrators
	 *   * synchronizes the Members
	 *   * synchronizes the Process Roles
	 *   
	 * @throws NamingException
	 */
	public void syncUsers() throws NamingException {
		try {
			IProcessArea pa = rtc.getProjectArea(getName());

			IProcessItemService service = (IProcessItemService)rtc.getTeamRepository().getClientLibrary(IProcessItemService.class);
			IProcessAreaWorkingCopy paWc = (IProcessAreaWorkingCopy)service.getWorkingCopyManager().createPrivateWorkingCopy(pa);
			
			// Administrators
			syncUsers("Administrators", paWc);
			
			// Members
			syncUsers("Members", paWc);
			
			// Process Roles
			syncProcessRoles(paWc);
			
			// Synchronize the child Team Areas
			Iterator<TeamArea> tas = children.iterator();
			while (tas.hasNext()) {
				TeamArea ta = tas.next();
				ta.syncUsers();
			}
			// TODO: Save the modified project or team area
			paWc.save(null);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		} catch (TeamRepositoryException e) {
			log.error("Project or Team Area: "+getName()+" does not exist");
		} catch (URISyntaxException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}


	/** Synchronize the users who are administrators or members of this project or team area.
	 * It is assumed that the user is already a member of the JTS, and has sufficient repository permissions.
	 * This part of LDAP-RTC user synchronization is done by the repotools_syncUsers command.
	 * 
	 * @param memberRole  of a user in an RTC project area: Administrators or Members
	 * @param pa the RTC project area (called an IProcessArea in the RTC SDK)
	 * 
	 * @throws NamingException
	 * @throws TeamRepositoryException 
	 */
	public void syncUsers(String memberRole, IProcessAreaWorkingCopy pa) throws NamingException, TeamRepositoryException {
		String racfGroupDN = (String)ta.get(memberRole);
		log.info("Syncing "+memberRole+" users from LDAP group: "+racfGroupDN);
		if (racfGroupDN == null) {
			log.warn("LDAP group for "+memberRole+" is not specified");
			return;
		}
		try {
			// Get the LDAP Users for this project or team area
			NamingEnumeration ldapUsers = ldapConnection.getContext().getAttributes(racfGroupDN).get("racfgroupuserids").getAll();

			// Get the current RTC users based on membership in the project or team area
			Map<String, IContributor> rtcMembers = rtc.getMembers(pa.getUnderlyingProcessArea(), memberRole);
			Map<String, IContributor> membersToRemove = new HashMap<String, IContributor>(rtcMembers);
			
			while (ldapUsers.hasMoreElements()) {
				String userDN = (String)ldapUsers.next();
				Attributes ldapUser = ldapConnection.getContext().getAttributes(userDN);
				if (ldapUser == null) {
					log.error("LDAP user: "+userDN+" is not defined in LDAP");
					continue;
				}
				String userId = ldapUser.get("racfid").get().toString();  
				
				// TODO: is the user's name racfprogrammername or something else?
				Attribute rawName = ldapUser.get("racfprogrammername");
				String name =  (rawName != null)? name = rawName.get().toString(): "UNKNOWN";
				
				// Examine the RTC users, adding, updating or marking for removal is needed
				if (!rtcMembers.containsKey(userId)) {
					// Add a new user
					log.info("Adding new user: "+userId+" ("+name+") to: "+pa.getName());
					rtc.addMember(pa, memberRole, userId);
				} else {
					membersToRemove.remove(userId); // don't remove this member
				}
				
			}
			// Remove the members that are not in the corresponding LDAP group
			Iterator<IContributor> removals = membersToRemove.values().iterator();
			while (removals.hasNext()) {
				IContributor member = removals.next();
				log.info("Removing user: "+member.getUserId()+" ("+member.getName()+"), email: "+member.getEmailAddress()+" to: "+pa.getName());
				rtc.removeMember(pa, memberRole, member);
			}
		} catch (NamingException e) {
			log.error("LDAP group: "+racfGroupDN+" does not exist");;
		}						
	}

	/** Synchronizes the process roles for this team area. The process roles in the configuration file
	 * must match the process roles for the process description defined for the ProjectArea.
	 * 
	 * @param p the project or team area to synchronize
	 * 
	 * TODO: implement the syncProcessRole method
	 */
	public void syncProcessRoles(IProcessAreaWorkingCopy p) {
		ta.get("Process Roles");
		log.info("Syncing process roles for "+p.getName()+" LDAP group: ");

	}
}
