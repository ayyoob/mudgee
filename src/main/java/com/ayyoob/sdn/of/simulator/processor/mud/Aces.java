package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Aces {

	@JsonProperty("ace")
	private List<Ace> aceList;

	public List<Ace> getAceList() {
		return aceList;
	}

	public void setAceList(List<Ace> aceList) {
		this.aceList = aceList;
	}
}
