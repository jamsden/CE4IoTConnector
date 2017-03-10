package com.ibm.repotools.utilities;

import java.util.Hashtable;
import java.util.Iterator;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ibm.team.process.common.IProcessArea;
import com.ibm.team.repository.client.IContributorManager;
import com.ibm.team.repository.client.ITeamRepository;
import com.ibm.team.repository.client.TeamPlatform;
import com.ibm.team.repository.common.IContributor;
import com.ibm.team.repository.common.TeamRepositoryException;

public class LDAP2RTCSync {

	private Logger log = LoggerFactory.getLogger(LDAP2RTCSync.class);

	private LdapRtcConfig config = null;  // The LDAP-RTC configuration file.
	private LdapConnection connection = null; // The LDAP directory connection from the above URI

	public static void main(String[] args) throws TeamRepositoryException {
		LDAP2RTCSync synchronizer = new LDAP2RTCSync();
		synchronizer.log.info("Testing LDAP Access");
		try {
			if (synchronizer.initialize(args)) {
				synchronizer.sync();
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} finally {
			TeamPlatform.shutdown();
		}
		synchronizer.log.info("Done");
	}

	private boolean initialize(String[] args) throws ParseException {

		try {
			Options options = new Options();
			options.addOption("c", "config", true, "LDAP - RTC users configuration file");

			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse(options, args);

			String configFile = cmd.getOptionValue("c");

			if (configFile == null) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("LDP2RTCSync", options);
				return false;
			}
			
			config = new LdapRtcConfig(configFile, log);
			if (config==null) {
				log.error("Unable to read: "+configFile);
				return false;
			}
			
			connection = config.getLDAPConnection();
			if (connection==null) {
				log.error("Missing LDAPConnection element in config file");
					return false;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;

	}
		
	private void sync() throws TeamRepositoryException {
		try {
			// Check the config file by printing the project areas in the server(s)
			Iterator<RTCServer> servers = config.getServers().iterator();
			while (servers.hasNext()) {
				RTCServer server = servers.next();
				log.info("Server: "+server.getServerURI());
				server.syncServerUsers();
				server.disconnect();
			}
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Just trying some RTC stuff to explore
	 * TODO: remove method tryRTCConnection
	 */
	private void tryRTCConnection() {
		try {
			RTCServer server = (RTCServer)config.getServers().toArray()[0];
			RTCUserOperations rtc = new RTCUserOperations(server, log);

			// Now do some operations:
			// Get all the project areas in the repository
			Iterator<ProjectArea> pas = server.getProjectAreas().iterator();
			while (pas.hasNext()) {
				ProjectArea pa = pas.next();
				log.info("    Project Area: "+pa.getName());
			}

			// Get the "Pet Store" Project Area
			IProcessArea petStore = rtc.getProjectArea("Pet Store");
			System.out.println(petStore.getName() + ": " + petStore.getDescription().getSummary());

			// Add a user to the team repository so they can be added as members to
			// project and team areas
			
			rtc.disconnect();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

}
