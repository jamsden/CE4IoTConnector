package com.ibm.repotools.utilities;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.List;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.slf4j.Logger;

import com.ibm.team.process.client.IProcessClientService;
import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.IContributorManager;
import com.ibm.team.repository.client.ILoginHandler2;
import com.ibm.team.repository.client.ILoginInfo2;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.client.login.UsernameAndPasswordLoginInfo;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;

public class RTCUserOperations {
	
	private RTCServer server = null;
	private Logger log = null;
	private ITeamRepository teamRepository = null;
	private IContributorManager contributorManager = null;
	private IProgressMonitor progressMonitor = new NullProgressMonitor();

	
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
			contributorManager = teamRepository.contributorManager();


			log.info("Login success: [" + teamRepository.loggedIn() + "]");			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void disconnect() {
		teamRepository.logout();
	}

	public static ILoginHandler2 getLoginHandler(final String user, final String password) {
		return new ILoginHandler2() {
			public ILoginInfo2 challenge(ITeamRepository repo) {
				return new UsernameAndPasswordLoginInfo(user, password);
			}
		};
	}
	
	public List<IContributor> getUsers() throws TeamRepositoryException {
		return teamRepository.contributorManager().fetchAllContributors(progressMonitor);
	}

	public IProcessArea getProjectArea(String projectAreaName) throws TeamRepositoryException, UnsupportedEncodingException, URISyntaxException {
		URI uri = new URI(URLEncoder.encode(projectAreaName, "UTF-8").replaceAll("\\+", "%20"));
		IProcessClientService processService = (IProcessClientService)teamRepository.getClientLibrary(IProcessClientService.class);
		IProcessArea processArea = processService.findProcessArea(uri, IProcessClientService.ALL_PROPERTIES, progressMonitor);
		return processArea;
	}

	public static void getTeamArea(String teamAreaName) {
	}


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

	public void removeUser(String userId) {
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