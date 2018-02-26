package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class AccessList {

	@JsonProperty("access-list")
	private List<AccessDTO> accessDTOList;

	public List<AccessDTO> getAccessDTOList() {
		return accessDTOList;
	}

	public void setAccessDTOList(List<AccessDTO> accessDTOList) {
		this.accessDTOList = accessDTOList;
	}
}
