package com.ibm.repotools.utilities;

import org.json.simple.JSONObject;

public class TeamArea {

	private JSONObject pa = null;

	public TeamArea (JSONObject pa) {
		this.pa = pa;
	}

	public String getName() {
		if (pa == null) return null;
		return (String)pa.get("name");
	}

	public void syncAdministrators() {
		
	}

	public void syncMembers() {
		
	}

	public void syncProcessRoles() {
		
	}
}
