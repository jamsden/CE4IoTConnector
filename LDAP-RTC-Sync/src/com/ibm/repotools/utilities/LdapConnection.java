package com.ibm.repotools.utilities;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.ldap.InitialLdapContext;

import org.json.simple.JSONObject;

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
		return (String)ldapConnection.get("adminId");
	}
	
	public String getAdminPassword() {
		if (obj == null) return null;
		return (String)ldapConnection.get("adminPassword");
	}
	
	public DirContext getContext() {
		return ctx;
	}
	
}
