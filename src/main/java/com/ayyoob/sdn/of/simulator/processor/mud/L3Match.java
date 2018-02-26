package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class L3Match {

	@JsonProperty("ipv4")
	private IPV4Match ipv4Match;

	public IPV4Match getIpv4Match() {
		return ipv4Match;
	}

	public void setIpv4Match(IPV4Match ipv4Match) {
		this.ipv4Match = ipv4Match;
	}

}
