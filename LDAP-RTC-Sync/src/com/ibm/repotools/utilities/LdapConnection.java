/*
 *+------------------------------------------------------------------------+
 *| Licensed Materials - Property of IBM                                   |
 *| (C) Copyright IBM Corp. 2016.  All Rights Reserved.                    |
 *|                                                                        |
 *| US Government Users Restricted Rights - Use, duplication or disclosure |
 *| restricted by GSA ADP Schedule Contract with IBM Corp.                 |
 *+------------------------------------------------------------------------+
 */
package com.ibm.repotools.utilities;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;

import org.json.simple.JSONObject;

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
		return (String)ldapConnection.get("password");
	}
	
	/** The DirContext can be used to access this LDAP connection.
	 * 
	 * @return the DirContext
	 */
	public DirContext getContext() {
		return ctx;
	}
	
}
