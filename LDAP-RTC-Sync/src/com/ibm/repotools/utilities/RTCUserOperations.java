package com.ibm.repotools.utilities;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.slf4j.Logger;

import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.client.workingcopies.IProcessAreaWorkingCopy;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.IContributorManager;
import com.ibm.team.repository.client.IItemManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.IContributorHandle;
import com.ibm.team.repository.common.TeamRepositoryException;

/** Provides an implementation of the RTC operations needed by LDAP - RTC user synchronization
 *  
 * @author jamsden
 *
 */
public class RTCUserOperations {
	
	private RTCServer server = null;
	private Logger log = null;
	private ITeamRepository teamRepository = null;
	private IProcessClientService processClient = null;
	private IContributorManager contributorManager = null;
	private IProgressMonitor progressMonitor = new NullProgressMonitor();

	
	/** Provides the operations needed to sync users for the given RTCServer
	 * 
	 * The constructor starts up the RTC TeamPlatform if needed, logs into the
	 * team server, and establishes the required client APIs
	 * @param server
	 * @param log
	 */
	public RTCUserOperations(RTCServer server, Logger log) {
		this.server = server;
		this.log = log;
		
		// Startup the team platform unless its already started
		if (!TeamPlatform.isStarted()) {
			TeamPlatform.startup();
		}

		try {

			teamRepository = TeamPlatform.getTeamRepositoryService().getTeamRepository(server.getServerURI());
			teamRepository.registerLoginHandler(getLoginHandler(server.getAdmin(), server.getPassword()));
			teamRepository.login(progressMonitor);
			processClient = (IProcessClientService)teamRepository.getClientLibrary(IProcessClientService.class);

			contributorManager = teamRepository.contributorManager();
		} catch (Exception e) {
			log.error("Unable to login to: " + server.getServerURI());			
			e.printStackTrace();
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
	 * TODO: Need to be able to get users by repository permissions, not just all users
	 * 
	 * @throws TeamRepositoryException
	 */
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
		IProcessClientService processService = (IProcessClientService)teamRepository.getClientLibrary(IProcessClientService.class);
		IProcessArea processArea = processService.findProcessArea(uri, IProcessClientService.ALL_PROPERTIES, progressMonitor);
		return processArea;
	}

	/** Get a TeamArea within a ProjectArea or another TeamArea
	 * 
	 * TODO: Implement getTeamArea using a hierarchical name 
	 * @param teamAreaName
	 */
	public static void getTeamArea(String teamAreaName) {
	}


	/** Add a user to this team server. 
	 * 
	 * @param userId
	 * @param userName
	 * @param emailAddress
	 * @return
	 * @throws TeamRepositoryException
	 * 
	 * TODO: addUser needs to handle the repository permissions
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
	
	public Map<String, IContributor> getMembers(IProcessArea pa, String memberRole) throws TeamRepositoryException  {
		Map<String, IContributor> members = new HashMap<String, IContributor>();
		IContributorHandle[] contributors = null;
		if (memberRole.equals("Administrators")) {
			contributors = pa.getAdministrators();
		} else if (memberRole.equals("Members")) {
			contributors = pa.getMembers();
		} else {
			log.error("Invalid project area member role: "+memberRole);
			return null;
		}
		for (int c=0; c<contributors.length; c++) {
			IContributor contributor = (IContributor)teamRepository.itemManager().fetchCompleteItem(contributors[c], IItemManager.DEFAULT, progressMonitor);
			members.put(contributor.getUserId(), (IContributor)contributor.getWorkingCopy());
		}
		return members;
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
	public IContributorHandle addMember(IProcessAreaWorkingCopy pa, String memberRole, String userId) {
		IContributorHandle contributorHandle = null;
		try {
			contributorHandle = contributorManager.fetchContributorByUserId(userId, progressMonitor);
			if (contributorHandle == null) {
				log.error("User: "+userId+" is not a member of this server");
				return null;
			}
			if (memberRole.equals("Administrators")) {
				pa.getUnderlyingProcessArea().addAdministrator(contributorHandle);
			} else {
				pa.getUnderlyingProcessArea().addMember(contributorHandle);
			}
		} catch (TeamRepositoryException e) {
			log.error("User: "+userId+" is not a member of this server");
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
		}
		return contributor;
	}

	/** Remove a member from the Administrators or Members of a Project or Team Area.
	 * 
	 * @param pa  the project area the contributor is a member of
	 * @param memberRolethe role they play, Administrators or Members
	 * @param member the contributor to remove
	 */
	public void removeMember(IProcessAreaWorkingCopy pa, String memberRole, IContributor member) {
		if (memberRole.equals("Administrators")) {
			pa.getUnderlyingProcessArea().addAdministrator((IContributorHandle)member.getItemHandle());
		} else {
			pa.getUnderlyingProcessArea().addMember((IContributorHandle)member.getItemHandle());
		}
	}
	
	

	public void addProcessRole(String role, String userId) {
	}

	public void removeProcessRole(String role, String userId) {
	}

	public void assignClientAccessLicense(String licenseKey, String userId) {
	}

	public void unassignClientAccessLicense(String licenseKey, String userId) {
	}

}