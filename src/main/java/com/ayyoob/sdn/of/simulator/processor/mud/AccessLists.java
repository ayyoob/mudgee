package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccessLists {

	@JsonProperty("access-lists")
	private AccessList accessList;

	public AccessList getAccessList() {
		return accessList;
	}

	public void setAccessList(AccessList accessList) {
		this.accessList = accessList;
	}
}
