package com.ibm.repotools.utilities;

import java.util.Hashtable;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.PosixParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LDAP2RTCSync {

	private Logger log = LoggerFactory.getLogger(LDAP2RTCSync.class);
	private String directory = null; // LDAP Directory Server URI
	DirContext ctx = null; // The LDAP directory context from the above URI
	private String admin = null; // LDAP administrator DN
	private String password = null; // LDAP administrator password

	public static void main(String[] args) {
		LDAP2RTCSync synchronizer = new LDAP2RTCSync();
		synchronizer.log.info("Testing LDAP Access");
		try {
			if (synchronizer.initialize(args)) {

			}
			synchronizer.sync();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		synchronizer.log.info("Done");
	}

	private boolean initialize(String[] args) throws ParseException {

		try {
			Options options = new Options();
			options.addOption("d", "directory", true, "Directory Server URI (e.g., ldap://example.com:389)");
			options.addOption("a", "admin", true, "LDAP admin DN (e.g., uid=admin,ou=system");
			options.addOption("p", "password", true, "Admin bind password");

			CommandLineParser parser = new PosixParser();
			CommandLine cmd = parser.parse(options, args);

			directory = cmd.getOptionValue("d");
			admin = cmd.getOptionValue("a");
			password = cmd.getOptionValue("p");

			if (directory == null || admin == null || password == null) {
				HelpFormatter formatter = new HelpFormatter();
				formatter.printHelp("LDP2RTCSync", options);
				return false;
			}
			Hashtable<String, Object> env = new Hashtable<String, Object>();
			env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
			env.put(Context.PROVIDER_URL, directory);
			env.put(Context.SECURITY_PRINCIPAL, "uid=admin,ou=system");
			env.put(Context.SECURITY_CREDENTIALS, "password");
			ctx = new InitialDirContext(env);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;

	}
	
	private void sync() {
		try {
			// Lookup myself
			Attributes attrs = ctx.getAttributes("uid=jamsden,ou=User,ou=CLM,ou=system");
			log.info(attrs.get("cn").get().toString());
			
			// get all the bindings for the ce4iot server (license keys, project areas)
			NamingEnumeration<Binding> server = ctx.listBindings("cn=ce4iot,cn=Server,ou=CLM,ou=system");
			while (server.hasMore()) {
				Binding binding = (Binding)server.next();
				log.info(binding.getName() + ": " + binding.getObject().toString());
			}
		} catch (NamingException e) {
			e.printStackTrace();
		}
	}


}
