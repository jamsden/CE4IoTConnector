package com.ibm.repotools.utilities;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;

import org.apache.commons.httpclient.URIException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

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

public class RTCSDKExamples {
	public static void main(String[] args) throws TeamRepositoryException {

		// Startup the team platform unless its already started
		if (!TeamPlatform.isStarted()) {
			TeamPlatform.startup();
		}

		try {
			String rtcURL = "https://ce4iot.rtp.raleigh.ibm.com:9443/ccm";
			String user = "jamsden"; // must be in JazzAdmins group for these
										// samples to run
			String pass = "Matjas3cha";
			IProgressMonitor progressMonitor = new NullProgressMonitor();

			ITeamRepository teamRepository = TeamPlatform.getTeamRepositoryService().getTeamRepository(rtcURL);
			teamRepository.registerLoginHandler(getLoginHandler(user, pass));

			teamRepository.login(progressMonitor);

			System.out.println("Login success: [" + teamRepository.loggedIn() + "]");

			// Now do some operations:
			
			// Get all the project areas in the repository
			
			// Get the "Pet Store" Project Area
			IProcessArea petStore = getProjectArea(teamRepository, "Pet Store", progressMonitor);
			System.out.println(petStore.getName() + ": " + petStore.getDescription().getSummary());

			// Add a user to the team repository so they can be added as members to
			// project and team areas
			IContributorManager contributorManager = teamRepository.contributorManager();
			IContributor contributor = contributorManager.fetchContributorByUserId("fjohnson", progressMonitor);
			if (contributor == null) {
				System.out.println("Adding Fred Johnson");
				contributor = addUser(teamRepository, "fjohnson", "Fred Johnson", "fjohnson@example.com");
			} else {
				System.out.println("Contributor: " + contributor.getName() + " already exists");
			}
			
			teamRepository.logout();

			System.out.println("Logout success: [" + teamRepository.loggedIn() + "]");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally {
			// be sure to shutdown the team platform
			TeamPlatform.shutdown();			
		}
	}

	public static ILoginHandler2 getLoginHandler(final String user, final String password) {
		return new ILoginHandler2() {
			public ILoginInfo2 challenge(ITeamRepository repo) {
				return new UsernameAndPasswordLoginInfo(user, password);
			}
		};
	}

	private static IProcessArea getProjectArea(ITeamRepository repo, String projectAreaName, IProgressMonitor progressMonitor) throws TeamRepositoryException, UnsupportedEncodingException, URISyntaxException {
		URI uri = new URI(URLEncoder.encode(projectAreaName, "UTF-8").replaceAll("\\+", "%20"));
		IProcessClientService processService = (IProcessClientService)repo.getClientLibrary(IProcessClientService.class);
		IProcessArea processArea = processService.findProcessArea(uri, IProcessClientService.ALL_PROPERTIES, progressMonitor);
		return processArea;
	}

	private static void getTeamArea() {
	}


	private static IContributor addUser(ITeamRepository repo, String userId, String userName, String emailAddress) {
		// Create Item Type Contributor and set its properties
		IContributor i1 = (IContributor) IContributor.ITEM_TYPE.createItem();
		i1.setName(userName);
		i1.setUserId(userId);
		i1.setEmailAddress(emailAddress);
		i1.setArchived(false);
		IContributor contributor = null;
		try {
			contributor = repo.contributorManager().saveContributor(i1, null);
		} catch (TeamRepositoryException e) {
			e.printStackTrace();
		}
		return contributor;
	}

	private static void removeUser() {
	}

	private static void addProcessRole() {
	}

	private static void removeProcessRole() {
	}

	private static void assignClientAccessLicense() {
	}

	private static void unassignClientAccessLicense() {
	}

}