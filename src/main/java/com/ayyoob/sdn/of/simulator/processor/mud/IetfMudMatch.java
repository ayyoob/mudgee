package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IetfMudMatch {

	@JsonProperty("local-networks")
	private List<String> localNetworks;

	private String controller;

	public String getController() {
		return controller;
	}

	public void setController(String controller) {
		this.controller = controller;
	}

	public List<String> getLocalNetworks() {
		return localNetworks;
	}

	public void setLocalNetworks(List<String> localNetworks) {
		this.localNetworks = localNetworks;
	}

}
