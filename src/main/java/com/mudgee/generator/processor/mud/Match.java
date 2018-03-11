/*
 * Copyright (c) 2018, UNSW. (https://www.unsw.edu.au/) All Rights Reserved.
 *
 * UNSW. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.mudgee.generator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Match {

	@JsonProperty("ietf-mud:mud")
	private IetfMudMatch ietfMudMatch;

	@JsonProperty("ipv4")
	private IPV4Match ipv4Match;

	@JsonProperty("ipv6")
	private IPV6Match ipv6Match;

	@JsonProperty("udp")
	private UdpMatch udpMatch;

	@JsonProperty("tcp")
	private TcpMatch tcpMatch;

	@JsonProperty("icmp")
	private IcmpMatch icmpMatch;

	@JsonProperty("eth")
	private EthMatch ethMatch;

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

	public IPV4Match getIpv4Match() {
		return ipv4Match;
	}

	public void setIpv4Match(IPV4Match ipv4Match) {
		this.ipv4Match = ipv4Match;
	}

	public IcmpMatch getIcmpMatch() {
		return icmpMatch;
	}

	public void setIcmpMatch(IcmpMatch icmpMatch) {
		this.icmpMatch = icmpMatch;
	}

	//	@JsonProperty("l3")
//	private L3Match l3Match;
//
//	@JsonProperty("l4")
//	private L4Match l4Match;
//
//	public L3Match getL3Match() {
//		return l3Match;
//	}
//
//	public void setL3Match(L3Match l3Match) {
//		this.l3Match = l3Match;
//	}
//
//	public L4Match getL4Match() {
//		return l4Match;
//	}
//
//	public void setL4Match(L4Match l4Match) {
//		this.l4Match = l4Match;
//	}


	public EthMatch getEthMatch() {
		return ethMatch;
	}

	public void setEthMatch(EthMatch ethMatch) {
		this.ethMatch = ethMatch;
	}

	public IPV6Match getIpv6Match() {
		return ipv6Match;
	}

	public void setIpv6Match(IPV6Match ipv6Match) {
		this.ipv6Match = ipv6Match;
	}

	public IetfMudMatch getIetfMudMatch() {
		return ietfMudMatch;
	}

	public void setIetfMudMatch(IetfMudMatch ietfMudMatch) {
		this.ietfMudMatch = ietfMudMatch;
	}
}
