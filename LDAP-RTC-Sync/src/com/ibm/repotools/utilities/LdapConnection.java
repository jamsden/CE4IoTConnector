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
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;

import org.json.simple.JSONObject;

import com.ibm.team.repository.common.util.ObfuscationHelper;

/** Supports the connection to an LDAP server as specified in the configuration file.
 * 
 * @author jamsden
 *
 */
public class LdapConnection {
	private JSONObject obj = null;
	private JSONObject ldapConnection = null;
	private DirContext ctx = null; // The LDAP directory context from the above URI
	
	public LdapConnection(JSONObject obj) {
		this.obj = obj;
		if (obj == null) return;
		ldapConnection = (JSONObject) obj.get("LDAPConnection");
		
		Hashtable<String, Object> env = new Hashtable<String, Object>();
		env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		env.put(Context.PROVIDER_URL, this.getURI());
		env.put(Context.SECURITY_PRINCIPAL, this.getAdminId());
		env.put(Context.SECURITY_CREDENTIALS, this.getAdminPassword());
		try {
			ctx = new InitialLdapContext(env, null);
		} catch (NamingException e) {
			e.printStackTrace();
		}

	}
	
	public String getURI() {
		if (obj == null) return null;
		return (String)ldapConnection.get("URI");
	}
	
	public String getAdminId() {
		if (obj == null) return null;
		return (String)ldapConnection.get("userId");
	}
	
	public String getAdminPassword() {
		if (obj == null) return null;
		String password = "********"; // don't return a null password
		try {
			password = ObfuscationHelper.decryptString((String)ldapConnection.get("password"));
		} catch (UnsupportedEncodingException | GeneralSecurityException e) {
			// ignore decoding errors
		}
		return password;
	}
	
	/** The DirContext can be used to access this LDAP connection.
	 * 
	 * @return the DirContext
	 */
	public DirContext getContext() {
		return ctx;
	}
	
	/** Get all the members of an LDAP group, including members of its subgroups.
	 * 
	 * @param groupDN the group Distinguished Name
	 * @return a List<String> of all the members  (empty if the group does not have any members)
	 * 
	 * @throws NamingException
	 */
	public List<String> getMembers(String groupDN) throws NamingException {
		List<String> result = new ArrayList<String>();
		// Add all the members of this group
		Attribute members = ctx.getAttributes(groupDN).get("racfgroupuserids");
		NamingEnumeration ldapUsers = (members != null)? members.getAll(): null;
		while (ldapUsers != null && ldapUsers.hasMoreElements()) {
			result.add((String)ldapUsers.next());
		}

		// now recursively do all the subgroups
		NamingEnumeration subgroups = null;
		Attribute groups = ctx.getAttributes(groupDN).get("racfsubgroupname");
		if (groups != null) subgroups = groups.getAll(); 
		while (subgroups != null && subgroups.hasMoreElements()) {
			result.addAll(getMembers((String)subgroups.next()));
		}
		return result;
	}
}
