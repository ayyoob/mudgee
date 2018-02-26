package com.ayyoob.sdn.of.simulator.apps.mudflowdto;

import com.ayyoob.sdn.of.simulator.OFFlow;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceFlowMap {
	private static int MAXIP_PER_DNS = 15;

	private Map<String, List<String>> dnsIpMap = new HashMap<>();
	private List<OFFlow> fromDeviceFlows;
	private List<OFFlow> toDeviceFlows;

	public Map<String, List<String>> getDnsIpMap() {
		return dnsIpMap;
	}

	public void setDnsIpMap(Map<String, List<String>> dnsIpMap) {
		this.dnsIpMap = dnsIpMap;
	}

	public List<OFFlow> getFromDeviceFlows() {
		return fromDeviceFlows;
	}

	public void setFromDeviceFlows(List<OFFlow> fromDeviceFlows) {
		this.fromDeviceFlows = fromDeviceFlows;
	}

	public List<OFFlow> getToDeviceFlows() {
		return toDeviceFlows;
	}

	public void setToDeviceFlows(List<OFFlow> toDeviceFlows) {
		this.toDeviceFlows = toDeviceFlows;
	}

	public void addDnsIps(String dns, List<String> ips) {
		if (dnsIpMap.get(dns) == null) {
			dnsIpMap.put(dns, ips);
		} else {
			List<String> ipholder = dnsIpMap.get(dns);
			if (ipholder.size() + ips.size() > MAXIP_PER_DNS) {
				int toRemove = MAXIP_PER_DNS - (ipholder.size() + ips.size());
				if (toRemove > 0) {
					for (int i = 0; i < toRemove; i++) {
						ipholder.remove(0);
					}
				}
			}
			ipholder.addAll(ips);
			dnsIpMap.put(dns, ipholder);
		}
	}

	public String getDns(String ip) {
		for (String dns : dnsIpMap.keySet()) {
			if (dnsIpMap.get(dns).contains(ip)) {
				return dns;
			}
		}
		return null;
	}

}
