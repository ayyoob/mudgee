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
public class IPV6Match {

	@JsonProperty("ietf-acldns:dst-dnsname")
	private String dstDnsName;

	@JsonProperty("ietf-acldns:src-dnsname")
	private String srcDnsName;

	private int protocol;

	@JsonProperty("destination-ipv6-network")
	private String destinationIp;

	@JsonProperty("source-ipv6-network")
	private String sourceIp;

	public String getDstDnsName() {
		return dstDnsName;
	}

	public void setDstDnsName(String dstDnsName) {
		this.dstDnsName = dstDnsName;
	}

	public String getSrcDnsName() {
		return srcDnsName;
	}

	public void setSrcDnsName(String srcDnsName) {
		this.srcDnsName = srcDnsName;
	}

	public int getProtocol() {
		return protocol;
	}

	public void setProtocol(int protocol) {
		this.protocol = protocol;
	}

	public String getDestinationIp() {
		return destinationIp;
	}

	public void setDestinationIp(String destinationIp) {
		this.destinationIp = destinationIp;
	}

	public String getSourceIp() {
		return sourceIp;
	}

	public void setSourceIp(String sourceIp) {
		this.sourceIp = sourceIp;
	}
}
