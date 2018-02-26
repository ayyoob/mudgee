package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AccessControlListHolder {

	private String name;
	private String type;
	private Aces aces;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public Aces getAces() {
		return aces;
	}

	public void setAces(Aces aces) {
		this.aces = aces;
	}
}
