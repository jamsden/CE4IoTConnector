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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.slf4j.Logger;

/** A class for abstracting and reading the LDAP - RTC user synchronization configuration file.
 * 
 * @author jamsden
 *
 */
public class LdapRtcConfig {

	private JSONObject obj = null;
	private LdapConnection ldapConnection = null;
	private Logger log = null;

	/** Reads and parses the configuration file and readies it for subsequent uses.
	 * 
	 * @param configFile - The LDAP-RTC user configuration JSON file name
	 * 
	 * @param log
	 */
	public LdapRtcConfig(String configFile, Logger log) {
		this.log = log;
		JSONParser parser = new JSONParser();
		try {
			String filePath = new File("").getAbsolutePath().concat(File.separator).concat(configFile);
			obj = (JSONObject)parser.parse(new FileReader(filePath));
	    } catch (FileNotFoundException e) {
	        e.printStackTrace();
	    } catch (IOException e) {
	        e.printStackTrace();
	    } catch (org.json.simple.parser.ParseException e) {
	        e.printStackTrace();
	    }
	}
	
	/** Get the LDAP connection from the LDAP-RTC user sync configuration file
	 * 
	 * @return a new LdapConnection that is ready for access.
	 */
	public LdapConnection getLDAPConnection() {
		if (ldapConnection == null) {
            ldapConnection = new LdapConnection(obj);
		}
		return ldapConnection;
	}
	
	/** Get the RTC server elements defined in the configuration file
	 * 
	 * @return Collection of RTC servers specified in the configuration file
	 */
	public Collection<RTCServer> getServers() {
		List<RTCServer> servers = new ArrayList<RTCServer>();
		Iterator<JSONObject> srvs = ((JSONArray)obj.get("RTCServers")).iterator();
		while (srvs.hasNext()) {
			servers.add(new RTCServer(srvs.next(), ldapConnection, log));
		}
		return servers;
	}
}
