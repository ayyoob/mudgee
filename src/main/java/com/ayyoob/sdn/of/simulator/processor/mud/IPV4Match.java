package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class IPV4Match {

	@JsonProperty("ietf-acldns:dst-dnsname")
	private String dstDnsName;

	@JsonProperty("ietf-acldns:src-dnsname")
	private String srcDnsName;

	private int protocol;

	@JsonProperty("destination-ipv4-network")
	private String destinationIp;

	@JsonProperty("source-ipv4-network")
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
