package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class IetfAccessControlListHolder {

	@JsonProperty("acl")
	private List<AccessControlListHolder> accessControlListHolder;

	public List<AccessControlListHolder> getAccessControlListHolder() {
		return accessControlListHolder;
	}

	public void setAccessControlListHolder(List<AccessControlListHolder> accessControlListHolder) {
		this.accessControlListHolder = accessControlListHolder;
	}
}
