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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.security.auth.login.LoginException;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.slf4j.Logger;

import com.ibm.team.process.client.IClientProcess;
import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.IProcessItemService;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.process.common.IProcessItem;
import com.ibm.team.process.common.IRole;
import com.ibm.team.repository.client.IContributorManager;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.client.util.IClientLibraryContext;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.IContributorLicenseType;
import com.ibm.team.repository.common.ILicenseAdminService;
import com.ibm.team.repository.common.TeamRepositoryException;

/** Provides an implementation of the RTC operations needed by LDAP - RTC user synchronization
 *  
 * @author jamsden
 *
 */
public class RTCUserOperations {
	
	private Logger log = null;
	private ITeamRepository teamRepository = null;
	private IProcessClientService processClient = null;
	private IContributorManager contributorManager = null;
	private IProgressMonitor progressMonitor = new NullProgressMonitor();
	private IProcessItemService itemService = null;
	private ILicenseAdminService licenseAdminService = null;
	private IContributorLicenseType[] validContributorLicenseTypes = null;
	
	/** Provides the operations needed to sync users for the given RTCServer
	 * 
	 * The constructor starts up the RTC TeamPlatform if needed, logs into the
	 * team server, and establishes the required client APIs
	 * @param server
	 * @param log
	 * @throws LoginException 
	 */
	public RTCUserOperations(RTCServer server, Logger log) throws LoginException {
		this.log = log;
		
		// Startup the team platform unless its already started
		if (!TeamPlatform.isStarted()) {
			TeamPlatform.startup();
		}

		try {

			teamRepository = TeamPlatform.getTeamRepositoryService().getTeamRepository(server.getServerURI());
			teamRepository.registerLoginHandler(getLoginHandler(server.getAdmin(), server.getPassword()));
			teamRepository.login(progressMonitor);
			
			licenseAdminService = (ILicenseAdminService) ((IClientLibraryContext) teamRepository).getServiceInterface(ILicenseAdminService.class);
			validContributorLicenseTypes = licenseAdminService.getLicenseTypes();
			processClient = (IProcessClientService)teamRepository.getClientLibrary(IProcessClientService.class);
			contributorManager = teamRepository.contributorManager();
			itemService = (IProcessItemService) teamRepository.getClientLibrary(IProcessItemService.class);
			
		} catch (Exception e) {
			log.error("Unable to login to: " + server.getServerURI());
			Status.appStatus.setCode(-1);
			throw new LoginException("Unable to login to: " + server.getServerURI());
		}
		
	}
	
	
	/** Disconnect (logout) from an RTC server.
	 * 
	 */
	public void disconnect() {
		teamRepository.logout();
	}
	

	/** A login handler for the TeamRepository
	 * @param user
	 * @param password
	 * @return
	 */
	public static ILoginHandler2 getLoginHandler(final String user, final String password) {
		return new ILoginHandler2() {
			public ILoginInfo2 challenge(ITeamRepository repo) {
				return new UsernameAndPasswordLoginInfo(user, password);
			}
		};
	}
	
	
	/**
	 * @return the team repository
	 */
	public ITeamRepository getTeamRepository () {
		return teamRepository;
	}
	
	/**
	 * @return a List of the users registered with this server
	 * 
	 * @throws TeamRepositoryException
	 */
	@SuppressWarnings("unchecked")
	public List<IContributor> getUsers() throws TeamRepositoryException {
		return teamRepository.contributorManager().fetchAllContributors(progressMonitor);
	}
	
	
	/** Get an RTC ProjectArea (called a ProcessArea in the RTC SDK).
	 * 
	 * @param projectAreaName
	 * @return
	 * @throws TeamRepositoryException
	 * @throws UnsupportedEncodingException
	 * @throws URISyntaxException
	 */
	public IProcessArea getProjectArea(String projectAreaName) throws TeamRepositoryException, UnsupportedEncodingException, URISyntaxException {
		URI uri = new URI(URLEncoder.encode(projectAreaName, "UTF-8").replaceAll("\\+", "%20"));
		IProcessArea processArea = (IProcessArea)processClient.findProcessArea(uri, IProcessClientService.ALL_PROPERTIES, progressMonitor);
		if (processArea == null) {
			log.error("Project area "+projectAreaName+" not found.");
			Status.appStatus.setCode(-1);
		}
		return processArea;
	}

	/** Add a user to this team server. 
	 * 
	 * @param userId
	 * @param userName
	 * @param emailAddress
	 * @return
	 * @throws TeamRepositoryException
	 * 
	 */
	public IContributor addUser(String userId, String userName, String emailAddress) throws TeamRepositoryException {
		// Create Item Type Contributor and set its properties
		IContributor contributor = contributorManager.fetchContributorByUserId(userId, null);
		if (contributor != null) return contributor;

		IContributor i1 = (IContributor) IContributor.ITEM_TYPE.createItem();
		i1.setUserId(userId);
		i1.setName(userName);
		i1.setEmailAddress(emailAddress);
		i1.setArchived(false);
		contributor = null;
		try {
			contributor = teamRepository.contributorManager().saveContributor(i1, progressMonitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		}
		return contributor;
	}

	/** Archive a user that should no longer have access to this team server
	 * @param userId
	 * @throws TeamRepositoryException 
	 */
	public void archiveUser(String userId) throws TeamRepositoryException {
		// Create Item Type Contributor and set its properties
		IContributor contributor = contributorManager.fetchContributorByUserId(userId, null);
		if (contributor == null) return;
		contributor.setArchived(true);
	}
	

	/** Get the administrators or members of a project area
	 * @param pa
	 * @param memberRole
	 * @return a Map of the userId, IContributor members
	 * @throws TeamRepositoryException
	 */
	public Map<String, IContributor> getMembers(IProcessArea pa, String memberRole) throws TeamRepositoryException  {
		Map<String, IContributor> members = new HashMap<String, IContributor>();
		IContributorHandle[] contributors = null;
		if (memberRole.equals("Administrators")) {
			contributors = pa.getAdministrators();
		} else if (memberRole.equals("Members")) {
			contributors = pa.getMembers();
		} else {
			log.error("Invalid project area member role: "+memberRole);
			Status.appStatus.setCode(-1);
			return null;
		}
		for (int c=0; c<contributors.length; c++) {
			IContributor contributor = (IContributor)teamRepository.itemManager().fetchCompleteItem(contributors[c], IItemManager.DEFAULT, progressMonitor);
			members.put(contributor.getUserId(), (IContributor)contributor.getWorkingCopy());
		}
		return members;
	}
	
	public IContributor getContributor(IContributorHandle contributorHandle) {
		IContributor contributor = null;
		try {
			contributor = (IContributor)teamRepository.itemManager().fetchCompleteItem(contributorHandle, IItemManager.DEFAULT, progressMonitor);
		} catch (TeamRepositoryException e) {
			Status.appStatus.setCode(-1);
			e.printStackTrace();
		}
		return contributor;
		
	}
	
	/** Add a new Administrators or Members member to the project area, The new member
	 * may also have their name and email address updated.
	 * 
	 * @param pa the project area the contributor is a member of
	 * @param memberRole the role they play, Administrators or Members
	 * @param contributor the contributor to update
	 * @param name updated name
	 * @param email updated email address
	 * @return the (possibly) updated contributor
	 * @throws TeamRepositoryException 
	 */
	public IContributorHandle addMember(IProcessArea pa, String memberRole, String userId) {
		IContributorHandle contributorHandle = null;
		try {
			contributorHandle = contributorManager.fetchContributorByUserId(userId, progressMonitor);
			if (memberRole.equals("Administrators")) {
				pa.addAdministrator(contributorHandle);
			} else {
				pa.addMember(contributorHandle);
			}
		} catch (TeamRepositoryException e) {
			log.error("User: "+userId+" is not a member of this server");
			Status.appStatus.setCode(-1);
		}
		return contributorHandle;
	}
	
	/** Update a project or team area administrator or member's name and/or email address
	 * 
	 * @param pa the project area the contributor is a member of
	 * @param memberRole the role they play, Administrators or Members
	 * @param contributor the contributor to update
	 * @param name updated name
	 * @param email updated email address
	 * 
	 * @return the updated contributor
	 */
	public IContributor updateMember(IProcessArea pa, String memberRole, IContributor contributor, String name, String email) {
		contributor.setName(name);
		contributor.setEmailAddress(email);
		contributor.setArchived(false);
		contributor = null;
		try {
			contributor = teamRepository.contributorManager().saveContributor(contributor, progressMonitor);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
			Status.appStatus.setCode(-1);
		}
		return contributor;
	}

	/** Remove a member from the Administrators or Members of a Project or Team Area.
	 * 
	 * @param pa  the project area the contributor is a member of
	 * @param memberRolethe role they play, Administrators or Members
	 * @param member the contributor to remove
	 */
	public void removeMember(IProcessArea pa, String memberRole, IContributor member) {
		if (memberRole.equals("Administrators")) {
			pa.removeAdministrator((IContributorHandle)member.getItemHandle());
		} else {
			pa.removeMember((IContributorHandle)member.getItemHandle());
		}
	}
	
	
	/** Get the a list of the process roles (names) for a user in a project area
	 * @param p the project area
	 * @param userId the user's ID
	 * @return a list of role names for the user in this project area
	 * @throws TeamRepositoryException 
	 */
	public List<IRole> getRoleAssignments(IProcessArea p, String userId)  {
		List<IRole> roleAssignments = new ArrayList<IRole>();
		try {
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);
			IClientProcess clientProcess = itemService.getClientProcess(p, null);

			roleAssignments.addAll(Arrays.asList(p.getRoleAssignments(user, clientProcess.getRoles(p, null))));
			return roleAssignments;
		} catch (TeamRepositoryException e) {
			log.error("Cannot get process roles for user: "+userId);
			Status.appStatus.setCode(-1);
		}
		return roleAssignments;
	}

	
	/** Add a process role to the user in this project area
	 * @param p the project area
	 * @param roleID the role to add
	 * @param userId the user to add the role to
	 * @throws TeamRepositoryException 
	 */
	public void addProcessRole(IProcessArea p, String roleID, String userId)  {
		try {
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);
			IRole role = getRole(p, roleID, progressMonitor);
			IProcessArea pi = (IProcessArea) itemService.getMutableCopy(p);
			pi.addRoleAssignments(user, new IRole[] { role });

			itemService.save(new IProcessItem[] { pi }, progressMonitor);
		} catch (TeamRepositoryException e) {
			log.error("Unable to add process role: {} to user: {} due to: {}", roleID, userId, e.getMessage());
			Status.appStatus.setCode(-1);
		}
	}
	

	/** Remove a process role from the user in this project area
	 * @param p the project area
	 * @param roleID the role to remove
	 * @param userId the user to add the role to
	 * @throws TeamRepositoryException 
	 */
	public void removeProcessRole(IProcessArea p, String roleID, String userId)  {
		try {
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);
			IRole role = getRole(p, roleID, progressMonitor);
			IProcessArea pi = (IProcessArea) itemService.getMutableCopy(p);
			pi.removeRoleAssignments(user, new IRole[] { role });

			itemService.save(new IProcessItem[] { pi }, progressMonitor);
		} catch (TeamRepositoryException e) {
			log.error("Unable to remove process role: {} from user: {} due to: ", roleID, userId, e.getMessage());
			Status.appStatus.setCode(-1);
		}
	}

	/**
	 * Gets a role by its ID.
	 * 
	 * @param area
	 * @param roleID
	 * @param monitor
	 * @return
	 * @throws TeamRepositoryException
	 */
	private IRole getRole(IProcessArea area, String roleID, IProgressMonitor monitor) throws TeamRepositoryException {
		IRole result = null;
		IClientProcess clientProcess = itemService.getClientProcess(area, monitor);
		IRole[] availableRoles = clientProcess.getRoles(area, monitor);
		for (int i = 0; i < availableRoles.length; i++) {
			IRole role = availableRoles[i];
			if (role.getId().equalsIgnoreCase(roleID)) {
				result = role;
				break;
			}
		}
		return result;
	}
	
	/** Get a list of contributors assigned a given Client Access License
	 * @param cla
	 * @return
	 */
	public IContributorHandle[] getContributorsAssignedLicense(String cla) {
		IContributorHandle[] licensedContributors = null;
		try {
			return licensedContributors = licenseAdminService.getLicensedContributors(getLicenseId(cla));
		} catch (TeamRepositoryException e) {
			log.error("Cannot get users assigned to CLA: "+cla);
			Status.appStatus.setCode(-1);
		} catch (Exception e) {
			log.error("Cannot get users assigned to CLA: "+cla);		
			Status.appStatus.setCode(-1);
		}
		return licensedContributors;
	}
	
	
	/** Get the client access licenses assigned to to a user.
	 * 
	 * License IDs are identifiers like com.ibm.team.rtc.developer. 
	 * License Keys are user identifiers made up of product names - name for the license.
	 * These have to be translated carefully.
	 * 
	 * @param userId the user we're getting the assigned licenses for
	 * @return the client access license ids assigned to this user
	 */
	public List<String> getAssignedClientAccessLicenses(String userId) {
		List<String> assignedLicenses = new ArrayList<String>();
		try {
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);
			assignedLicenses.addAll(Arrays.asList(licenseAdminService.getAssignedLicenses(user)));
		} catch (TeamRepositoryException e) {
			log.error("Cannot get licenses for user: "+userId);
			Status.appStatus.setCode(-1);
		} catch (Exception e) {
			log.error("Cannot get licenses for for user: "+userId);
			Status.appStatus.setCode(-1);
		}
		return assignedLicenses;
		
	}
	
	/** Get the license ID for a license key
	 * @param licenseKey (e.g., Rational Team Concert - Developer)
	 * @return licenseKey (e.g., com.ibm.team.rtc.developer)  
	 */
	public String getLicenseId(String licenseKey) {
		for (int l=0; l<validContributorLicenseTypes.length; l++) {
			String license = validContributorLicenseTypes[l].getProductName()+" - "+validContributorLicenseTypes[l].getName();
			if (license.equals(licenseKey)) return validContributorLicenseTypes[l].getId();
		}
		log.info("Valid client access license keys are:");
		for (int l=0; l<validContributorLicenseTypes.length; l++) {
			log.info("\t"+validContributorLicenseTypes[l].getProductName()+" - "+validContributorLicenseTypes[l].getName());
		}		
		return null;
	}

	
	/** Assign a client access license to this user
	 * @param licenseId the license ID to assign (e.g., com.ibm.team.rtc.developer)
	 * @param userId the user who will be assigned the license
	 */
	public void assignClientAccessLicense(String licenseKey, String userId) {
		try {
			String licenseId = getLicenseId(licenseKey);
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);
			licenseAdminService.assignLicense(user, licenseId);
		} catch (TeamRepositoryException e) {
			log.error("Unable to assign client access license: "+licenseKey+" to user: "+userId);
			Status.appStatus.setCode(-1);
		}
	}

	
	/** Unassign a client access license from a user
	 * @param licenseId the license ID to unassign (e.g., com.ibm.team.rtc.developer)
	 * @param userId the user who will loose the license
	 */
	public void unassignClientAccessLicense(String licenseKey, String userId) {
		try {
			String licenseId = getLicenseId(licenseKey);
			IContributor user = teamRepository.contributorManager().fetchContributorByUserId(userId, progressMonitor);				licenseAdminService.unassignLicense(user, licenseId);
			licenseAdminService.unassignLicense(user, licenseId);
		} catch (TeamRepositoryException e) {
			log.error("Unable to unassign client access license: "+licenseKey+" from user: "+userId);
			Status.appStatus.setCode(-1);
		}
	}

}