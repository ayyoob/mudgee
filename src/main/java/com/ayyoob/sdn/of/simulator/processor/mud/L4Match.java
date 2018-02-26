package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class L4Match {

	@JsonProperty("udp")
	private UdpMatch udpMatch;

	@JsonProperty("tcp")
	private TcpMatch tcpMatch;

	public UdpMatch getUdpMatch() {
		return udpMatch;
	}

	public void setUdpMatch(UdpMatch udpMatch) {
		this.udpMatch = udpMatch;
	}

	public TcpMatch getTcpMatch() {
		return tcpMatch;
	}

	public void setTcpMatch(TcpMatch tcpMatch) {
		this.tcpMatch = tcpMatch;
	}
}
