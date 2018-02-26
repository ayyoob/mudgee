package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

public class MudSpec {

	@JsonProperty("ietf-mud:mud")
	private  IetfMud ietfMud;

	@JsonProperty("ietf-access-control-list:access-lists")
	private IetfAccessControlListHolder accessControlList;

	public IetfMud getIetfMud() {
		return ietfMud;
	}

	public void setIetfMud(IetfMud ietfMud) {
		this.ietfMud = ietfMud;
	}

	public IetfAccessControlListHolder getAccessControlList() {
		return accessControlList;
	}

	public void setAccessControlList(IetfAccessControlListHolder accessControlList) {
		this.accessControlList = accessControlList;
	}
}
