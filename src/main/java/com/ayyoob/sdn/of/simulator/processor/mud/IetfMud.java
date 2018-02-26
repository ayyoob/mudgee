package com.ayyoob.sdn.of.simulator.processor.mud;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({ "mud-version", "mud-url", "last-update", "cache-validity", "is-supported", "systeminfo", "from-device-policy", "to-device-policy" })
public class IetfMud {

	@JsonProperty("mud-version")
	private int mudVersion;

	@JsonProperty("mud-url")
	private String mudUrl;

	@JsonProperty("last-update")
	private String lastUpdate;

	@JsonProperty("cache-validity")
	private int cacheValidity;

	@JsonProperty("is-supported")
	private boolean supported;

	@JsonProperty("systeminfo")
	private String systeminfo;

	@JsonProperty("from-device-policy")
	private AccessLists fromDevicePolicy;

	@JsonProperty("to-device-policy")
	private AccessLists toDevicePolicy;

	public int getMudVersion() {
		return mudVersion;
	}

	public void setMudVersion(int mudVersion) {
		this.mudVersion = mudVersion;
	}

	public String getMudUrl() {
		return mudUrl;
	}

	public void setMudUrl(String mudUrl) {
		this.mudUrl = mudUrl;
	}

	public String getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(String lastUpdate) {
		this.lastUpdate = lastUpdate;
	}

	public int getCacheValidity() {
		return cacheValidity;
	}

	public void setCacheValidity(int cacheValidity) {
		this.cacheValidity = cacheValidity;
	}

	public boolean isSupported() {
		return supported;
	}

	public void setSupported(boolean supported) {
		this.supported = supported;
	}

	public String getSysteminfo() {
		return systeminfo;
	}

	public void setSysteminfo(String systeminfo) {
		this.systeminfo = systeminfo;
	}

	public AccessLists getFromDevicePolicy() {
		return fromDevicePolicy;
	}

	public void setFromDevicePolicy(AccessLists fromDevicePolicy) {
		this.fromDevicePolicy = fromDevicePolicy;
	}

	public AccessLists getToDevicePolicy() {
		return toDevicePolicy;
	}

	public void setToDevicePolicy(AccessLists toDevicePolicy) {
		this.toDevicePolicy = toDevicePolicy;
	}
}
