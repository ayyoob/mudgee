package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Match {

	@JsonProperty("ietf-mud:mud")
	private IetfMudMatch ietfMudMatch;

	@JsonProperty("l3")
	private L3Match l3Match;

	@JsonProperty("l4")
	private L4Match l4Match;

	public L3Match getL3Match() {
		return l3Match;
	}

	public void setL3Match(L3Match l3Match) {
		this.l3Match = l3Match;
	}

	public L4Match getL4Match() {
		return l4Match;
	}

	public void setL4Match(L4Match l4Match) {
		this.l4Match = l4Match;
	}

	public IetfMudMatch getIetfMudMatch() {
		return ietfMudMatch;
	}

	public void setIetfMudMatch(IetfMudMatch ietfMudMatch) {
		this.ietfMudMatch = ietfMudMatch;
	}
}
