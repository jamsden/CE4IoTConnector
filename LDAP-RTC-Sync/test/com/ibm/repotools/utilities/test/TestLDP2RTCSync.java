package com.ibm.repotools.utilities.test;

import org.junit.BeforeClass;
import org.junit.Test;

import com.ibm.repotools.utilities.LDAP2RTCSync;

public class TestLDP2RTCSync {

	/**
	 * Do an LDAP2RTCSync with RTC-Test-Initial.json to establish the initial
	 * conditions in the server, and example project and team areas.
	 * 
	 * There are no assertions here, we assume this works and test it indirectly
	 * with the test cases that depend on accurate and consistent initial conditions.
	 * 
	 * @throws Exception
	 */
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.out.println("Initializing LDAP2RTCSync tests: establishing initial RTC project area configurations");
		String[] args = new String[]{"--config", "RTC-Test-Initial.json"};
		LDAP2RTCSync.main(args);
	}

	/**
	 * Add Administrators to project and team areas
	 * Remove Administrators from project and team areas
	 * Attempt to add Administrator that is not a member of the server
	 * Attempt to add Administrator from an undefined LDAP group
	 * 
	 * Use this method to test everything for now. For future consideration:
	 * Change this to testAdministrators, test the functionality in different test cases,
	 * add assertions to check the results. 
	 */
	@Test
	public void testSyncing() throws Exception {
		// remove Administrator JAZZ and add TAMI
		System.out.println("\n\nTesting adding and removing Administrators, Members and Process Roles in project and team areas");
		String[] args = new String[]{"--config", "RTC-Test-Final.json"};
		LDAP2RTCSync.main(args);
		System.out.println("Test Administrators is complete\n");
	}

	/**
	 * Add Members to project and team areas
	 * Remove Members from project and team areas
	 * Attempt to add a Member that is not a member of the server
	 * Attempt to add Members from an undefined LDAP group
	 * Attempt to add Members to a non-existent project area or team area.
	 * Add all members from a hierarchical LDAP group
	 */
	@Test
	public void testMembers() {
		//fail("Not yet implemented");
	}

	/**
	 * Add Process Roles to users in a project and team area
	 * Remove Process roles from users in a project and team area
	 * Attempt to add an undefined process role
	 * Attempt to assign a process roles from an undefined LDAP group
	 * Attempt to add roles to users in a non-existent project area
	 */
	@Test
	public void testProcessRoles() {
		//fail("Not yet implemented");
	}

	/**
	 * Assign client access licenses to users in a server
	 * Unassign client access licenses from a user in a server
	 * Attempt to assign licenses to users in a non-existent server
	 * Attempt to assign licenses to a user who is not a user in a server
	 * Attempt to assign client access licenses from and undefined LDAP group
	 * Attempt to assign a non-existent license
	 * Attempt to assign a license for which there are no more instances available
	 */
	@Test
	public void testClientAccessLicenses() {
		//fail("Not yet implemented");
	}


}
