package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TcpMatch {

	@JsonProperty("destination-port-range-or-operator")
	private PortMatch destinationPortMatch;

	@JsonProperty("source-port-range-or-operator")
	private PortMatch sourcePortMatch;

	public PortMatch getDestinationPortMatch() {
		return destinationPortMatch;
	}

	public void setDestinationPortMatch(PortMatch destinationPortMatch) {
		this.destinationPortMatch = destinationPortMatch;
	}

	public PortMatch getSourcePortMatch() {
		return sourcePortMatch;
	}

	public void setSourcePortMatch(PortMatch sourcePortMatch) {
		this.sourcePortMatch = sourcePortMatch;
	}
}
