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
package com.mudgee.generator.processor;

import com.mudgee.generator.Constants;
import com.mudgee.generator.vswitch.OFFlow;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mudgee.generator.processor.mud.*;

import java.io.*;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;

import static com.mudgee.generator.Constants.*;

public class MUDGenerator {
	private static final String DEVICETAG = "<deviceMac>";
	private static final String GATEWAYTAG = "<gatewayMac>";
	private static final String DEFAULTGATEWAYCONTROLLER = "urn:ietf:params:mud:gateway";
	private static final String LOCAL_TAG = "localTAG";
	private static final String STUN_PROTO_PORT = "3478";
	private static final int MAX_IP_PER_PROTO = 3;
	// this is used to filter packet that was generated through other means such probing or attacking.
	private static final int MIN_PACKET_COUNT_THRESHOLD = 5;
	private static int ALLOWED_ICMP_TYPES[] = {8};
	private static Pattern VALID_IPV4_PATTERN = null;
	private static Pattern VALID_IPV6_PATTERN = null;
	private static final String ipv4Pattern = "(([01]?\\d\\d?|2[0-4]\\d|25[0-5])\\.){3}([01]?\\d\\d?|2[0-4]\\d|25[0-5])";
	private static final String ipv6Pattern = "([0-9a-f]{1,4}:){7}([0-9a-f]){1,4}";

	static {
		try {
			VALID_IPV4_PATTERN = Pattern.compile(ipv4Pattern, Pattern.CASE_INSENSITIVE);
			VALID_IPV6_PATTERN = Pattern.compile(ipv6Pattern, Pattern.CASE_INSENSITIVE);
		} catch (PatternSyntaxException e) {
			//logger.severe("Unable to compile pattern", e);
		}
	}


	public static void generate(String deviceName, String deviceMac, String defaultGatewayIp)
			throws JsonProcessingException, FileNotFoundException, UnsupportedEncodingException {
		List<OFFlow> fromDevice = new ArrayList<>();
		List<OFFlow> toDevice = new ArrayList<>();
		generateDeviceFlows(deviceName, deviceMac, fromDevice, toDevice);
		generateMud(deviceName, defaultGatewayIp, fromDevice, toDevice);

	}

	private static void generateDeviceFlows(String deviceName, String deviceMac,
											List<OFFlow> fromDevice, List<OFFlow> toDevice)
			throws JsonProcessingException, FileNotFoundException, UnsupportedEncodingException {
		String currentPath = Paths.get(".").toAbsolutePath().normalize().toString();
		String workingDirectory = currentPath + File.separator + "result"
				+ File.separator + deviceName + File.separator;
		File ipflowFile = new File(workingDirectory + deviceMac + "_ipflows.csv");

		Map<String, OFFlow> commonFlowMap = new HashMap<>();
		Map<String, OFFlow> fromDeviceMap = new HashMap<>();
		Map<String, OFFlow> toDeviceMap = new HashMap<>();
		List<OFFlow> localDevice = new ArrayList<>();

		boolean stunEnabled = false;

		try (BufferedReader br = new BufferedReader(new FileReader(ipflowFile))) {
			String line;
			br.readLine();
			while ((line = br.readLine()) != null) {
				// process the line.
				//"srcMac,dstMac,ethType,vlanId,srcIp,dstIp,ipProto,srcPort,dstPort,priority"
				if (!line.isEmpty()) {
					String vals[] = line.split(",");
					OFFlow ofFlow = new OFFlow();
					ofFlow.setSrcMac(vals[0]);
					ofFlow.setDstMac(vals[1]);
					ofFlow.setEthType(vals[2]);
					ofFlow.setVlanId(vals[3]);
					ofFlow.setSrcIp(vals[4]);
					ofFlow.setDstIp(vals[5]);
					ofFlow.setIpProto(vals[6]);
					ofFlow.setSrcPort(vals[7]);
					ofFlow.setDstPort(vals[8]);
					ofFlow.setPriority(Integer.parseInt(vals[9]));

					ofFlow.setIcmpType(vals[10]);
					ofFlow.setIcmpCode(vals[11]);
					ofFlow.setVolumeTransmitted(Long.parseLong(vals[12]));
					ofFlow.setPacketCount(Long.parseLong(vals[13]));
					if ( ofFlow.getPriority() == COMMON_FLOW_PRIORITY
							|| ofFlow.getPriority() == D2G_PRIORITY
							|| ofFlow.getPriority() == G2D_PRIORITY || ofFlow.getPriority() == L2D_PRIORITY) {
						//ignore
					} else {
						if (ofFlow.getPriority() > COMMON_FLOW_PRIORITY) {
							if (!ofFlow.getIcmpType().equals("*")
									&& IntStream.of(ALLOWED_ICMP_TYPES)
									.noneMatch(x -> x == Integer.parseInt(ofFlow.getIcmpType()))) {
								continue;
							}
							String key = ofFlow.getEthType() + "|" + ofFlow.getIpProto() + "|" + ofFlow.getDstPort()
									+ "|" + ofFlow.getIcmpCode() + "|" + ofFlow.getIcmpType();
							if ((validIP(ofFlow.getDstIp()) || ofFlow.getDstMac().equals(Constants.BROADCAST_MAC)
							||!(ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV4)
									|| ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)))
									&& ofFlow.getPacketCount() <= MIN_PACKET_COUNT_THRESHOLD) {
								continue;
							}
							if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)
									&& ofFlow.getDstIp().startsWith(LINK_LOCAL_ALL_NODE)) {
								ofFlow.setDstIp(LINK_LOCAL_MULTICAST_IP_RANGE);
							}
							OFFlow flow = commonFlowMap.get(key);
							if (flow == null) {
								commonFlowMap.put(key, ofFlow);
							} else {
								flow.setDstIp(flow.getDstIp() + "|" + ofFlow.getDstIp());
								commonFlowMap.put(key, flow);
							}
						} else if (ofFlow.getPriority() > D2G_PRIORITY && ofFlow.getPriority() < D2G_PRIORITY + 100) {
							if (validIP(ofFlow.getDstIp()) && ofFlow.getPacketCount() <= MIN_PACKET_COUNT_THRESHOLD) {
								continue;
							}
							String key = ofFlow.getIpProto() + "|" + ofFlow.getDstPort() + "|" + ofFlow.getSrcPort();
							if (ofFlow.getIpProto().equals(Constants.UDP_PROTO) && ofFlow.getDstPort().equals(STUN_PROTO_PORT)) {
								stunEnabled = true;
								fromDeviceMap.remove(key);
								ofFlow.setDstPort("*");
								ofFlow.setDstIp("*");
								key = ofFlow.getIpProto() + "|" + ofFlow.getDstPort() + "|" + ofFlow.getSrcPort();
								for (String keys : fromDeviceMap.keySet()) {
									if (keys.contains("17|")) {
										fromDeviceMap.remove(keys);
									}
								}
								fromDeviceMap.put(key, ofFlow);
								continue;
							} else if (stunEnabled && ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
								continue;
							}
							OFFlow flow = fromDeviceMap.get(key);
							if (flow == null) {
								fromDeviceMap.put(key, ofFlow);
							} else {
								flow.setDstIp(flow.getDstIp() + "|" + ofFlow.getDstIp());
								fromDeviceMap.put(key, flow);
							}
						} else if (ofFlow.getPriority() > G2D_PRIORITY && ofFlow.getPriority() < G2D_PRIORITY + 100) {
							if (validIP(ofFlow.getSrcIp()) && ofFlow.getPacketCount() <= MIN_PACKET_COUNT_THRESHOLD) {
								continue;
							}
							String key = ofFlow.getIpProto() + "|" + ofFlow.getDstPort() + "|" + ofFlow.getSrcPort();
							if (ofFlow.getIpProto().equals(Constants.UDP_PROTO) && ofFlow.getSrcPort().equals(STUN_PROTO_PORT)) {
								stunEnabled = true;
								toDeviceMap.remove(key);
								ofFlow.setSrcPort("*");
								ofFlow.setSrcIp("*");
								key = ofFlow.getIpProto() + "|" + ofFlow.getDstPort() + "|" + ofFlow.getSrcPort();

								for (String keys : toDeviceMap.keySet()) {
									if (keys.contains("17|")) {
										toDeviceMap.remove(keys);
									}
								}

								toDeviceMap.put(key, ofFlow);
								continue;
							} else if (stunEnabled && ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
								continue;
							}
							OFFlow flow = toDeviceMap.get(key);
							if (flow == null) {
								toDeviceMap.put(key, ofFlow);
							} else {
								flow.setSrcIp(flow.getSrcIp() + "|" + ofFlow.getSrcIp());
								toDeviceMap.put(key, flow);
							}
						} else if (ofFlow.getPriority() > L2D_PRIORITY && ofFlow.getPriority() < L2D_PRIORITY + 100) {
							if (ofFlow.getPacketCount() > MIN_PACKET_COUNT_THRESHOLD) {
								if (ofFlow.getSrcMac().equals(deviceMac)) {
									ofFlow.setSrcMac(DEVICETAG);
								} else if (ofFlow.getDstMac().equals(deviceMac)) {
									ofFlow.setDstMac(DEVICETAG);
								}
								localDevice.add(ofFlow);
							}
						}
					}

				}
			}

			//common flow
			for (String key : commonFlowMap.keySet()) {
				OFFlow ofFlow = commonFlowMap.get(key);
				Set<String> dsts = new HashSet<String>(Arrays.asList(ofFlow.getDstIp().split("\\|")));
				for (String dstLocation : dsts) {
					OFFlow deviceFlow = ofFlow.copy();
					if (deviceFlow.getPriority() == Constants.MULTICAST_BROADCAST_PRIORITY) {
						deviceFlow.setSrcMac(DEVICETAG);
						deviceFlow.setDstIp(dstLocation);
						if (!ofFlow.getDstMac().equals(Constants.BROADCAST_MAC)) {
							deviceFlow.setDstMac("*");
						}
						fromDevice.add(deviceFlow);
						continue;
					}
					deviceFlow.setSrcMac(DEVICETAG);
					deviceFlow.setDstMac(GATEWAYTAG);
					deviceFlow.setDstIp(dstLocation);
					fromDevice.add(deviceFlow);

					OFFlow reverseFlow = deviceFlow.copy();
					reverseFlow.setSrcMac(deviceFlow.getDstMac());
					reverseFlow.setDstMac(deviceFlow.getSrcMac());
					reverseFlow.setSrcIp(deviceFlow.getDstIp());
					reverseFlow.setDstIp(deviceFlow.getSrcIp());
					reverseFlow.setSrcPort(deviceFlow.getDstPort());
					reverseFlow.setDstPort(deviceFlow.getSrcPort());
					if (reverseFlow.getIcmpType().equals(Constants.ICMP_ECHO_TYPE)) {
						reverseFlow.setIcmpType(Constants.ICMP_ECHO_REPLY_TYPE);
					}
					toDevice.add(reverseFlow);
				}
			}

			// from device
			for (String key : fromDeviceMap.keySet()) {
				OFFlow ofFlow = fromDeviceMap.get(key);
				Set<String> dsts = new HashSet<String>(Arrays.asList(ofFlow.getDstIp().split("\\|")));
				int ipCounter = 0;
				for (String dstLocation : dsts) {
					if (validIP(dstLocation)) {
						ipCounter++;
					}
				}
				if (ipCounter >= MAX_IP_PER_PROTO) {
					OFFlow deviceFlow = ofFlow.copy();
					deviceFlow.setSrcMac(DEVICETAG);
					deviceFlow.setDstMac(GATEWAYTAG);
					deviceFlow.setDstIp("*");
					fromDevice.add(deviceFlow);
					continue;
				}
				for (String dstLocation : dsts) {
					OFFlow deviceFlow = ofFlow.copy();
					deviceFlow.setSrcMac(DEVICETAG);
					deviceFlow.setDstMac(GATEWAYTAG);
					deviceFlow.setDstIp(dstLocation);
					fromDevice.add(deviceFlow);
				}
			}

			// to device
			for (String key : toDeviceMap.keySet()) {
				OFFlow ofFlow = toDeviceMap.get(key);
				Set<String> dsts = new HashSet<String>(Arrays.asList(ofFlow.getSrcIp().split("\\|")));

				int ipCounter = 0;
				for (String dstLocation : dsts) {
					if (validIP(dstLocation)) {
						ipCounter++;
					}
				}
				if (ipCounter >= MAX_IP_PER_PROTO) {
					OFFlow deviceFlow = ofFlow.copy();
					deviceFlow.setDstMac(DEVICETAG);
					deviceFlow.setSrcMac(GATEWAYTAG);
					deviceFlow.setSrcIp("*");
					toDevice.add(deviceFlow);
					continue;
				}

				for (String dstLocation : dsts) {
					OFFlow deviceFlow = ofFlow.copy();
					deviceFlow.setDstMac(DEVICETAG);
					deviceFlow.setSrcMac(GATEWAYTAG);
					deviceFlow.setSrcIp(dstLocation);
					toDevice.add(deviceFlow);
				}
			}

			PrintWriter ruleOut = new PrintWriter(currentPath + File.separator + "result"
					+ File.separator + deviceName + File.separator + deviceName + "rule.csv", "UTF-8");
			ruleOut.println("srcMac,dstMac,ethType,srcIp,dstIp,ipProto,srcPort,dstPort,priority, icmpType, icmpCode");
			printList(fromDevice, ruleOut);
			printList(toDevice, ruleOut);
			printList(localDevice, ruleOut);
			ruleOut.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		for (OFFlow ofFlow : localDevice) {
			if (ofFlow.getSrcMac().equals(DEVICETAG)) {
				fromDevice.add(ofFlow);
			} else {
				toDevice.add(ofFlow);
			}
		}
	}

	private static void generateMud(String deviceName, String defaultGatewayIp, List<OFFlow> fromDevice,
									List<OFFlow> toDevice) throws FileNotFoundException, UnsupportedEncodingException, JsonProcessingException {
		String currentPath = Paths.get(".").toAbsolutePath().normalize().toString();
		//ipv4 from Device
		AccessControlListHolder fromIPv4DevicessAccesssListHolder = new AccessControlListHolder();
		fromIPv4DevicessAccesssListHolder.setType("ipv4-acl-type");
		String fromIpv4Id = "from-ipv4-" + deviceName.toLowerCase().replace(" ", "");
		fromIPv4DevicessAccesssListHolder.setName(fromIpv4Id);
		Aces ipv4FromAces = new Aces();
		List<Ace> fromIpv4aceList = getFromAces(fromDevice, fromIpv4Id, defaultGatewayIp, false);
		ipv4FromAces.setAceList(fromIpv4aceList);
		fromIPv4DevicessAccesssListHolder.setAces(ipv4FromAces);


		//ipv4 to Device
		AccessControlListHolder toIPv4DevicessAccesssListHolder = new AccessControlListHolder();
		toIPv4DevicessAccesssListHolder.setType("ipv4-acl-type");
		String toIp4Id = "to-ipv4-" + deviceName.toLowerCase().replace(" ", "");
		toIPv4DevicessAccesssListHolder.setName(toIp4Id);
		Aces ipv4ToAces = new Aces();
		List<Ace> toIpv4AceList = getToAces(toDevice, toIp4Id, defaultGatewayIp, false);
		ipv4ToAces.setAceList(toIpv4AceList);
		toIPv4DevicessAccesssListHolder.setAces(ipv4ToAces);

		//ipv6 from Device
		AccessControlListHolder fromIPv6DevicessAccesssListHolder = new AccessControlListHolder();
		fromIPv6DevicessAccesssListHolder.setType("ipv6-acl-type");
		String fromIpv6Id = "from-ipv6-" + deviceName.toLowerCase().replace(" ", "");
		fromIPv6DevicessAccesssListHolder.setName(fromIpv6Id);
		Aces ipv6FromAces = new Aces();
		List<Ace> fromIpv6aceList = getFromAces(fromDevice, fromIpv6Id, defaultGatewayIp, true);
		ipv6FromAces.setAceList(fromIpv6aceList);
		fromIPv6DevicessAccesssListHolder.setAces(ipv6FromAces);


		//ipv6 to Device
		AccessControlListHolder toIPv6DevicessAccesssListHolder = new AccessControlListHolder();
		toIPv6DevicessAccesssListHolder.setType("ipv6-acl-type");
		String toIp6Id = "to-ipv6-" + deviceName.toLowerCase().replace(" ", "");
		toIPv6DevicessAccesssListHolder.setName(toIp6Id);
		Aces ipv6ToAces = new Aces();
		List<Ace> toIpv6AceList = getToAces(toDevice, toIp6Id, defaultGatewayIp, true);
		ipv6ToAces.setAceList(toIpv6AceList);
		toIPv6DevicessAccesssListHolder.setAces(ipv6ToAces);


		AccessDTO fromIpv4AccessDTO = new AccessDTO();
		fromIpv4AccessDTO.setName("from-ipv4-" + deviceName.toLowerCase().replace(" ", ""));

		AccessDTO toIpv4accessDTO = new AccessDTO();
		toIpv4accessDTO.setName("to-ipv4-" + deviceName.toLowerCase().replace(" ", ""));

		AccessDTO fromIpv6AccessDTO = new AccessDTO();
		fromIpv6AccessDTO.setName("from-ipv6-" + deviceName.toLowerCase().replace(" ", ""));

		AccessDTO toIpv6accessDTO = new AccessDTO();
		toIpv6accessDTO.setName("to-ipv6-" + deviceName.toLowerCase().replace(" ", ""));

		IetfMud ietfMud = new IetfMud();
		ietfMud.setMudVersion(1);
		ietfMud.setMudUrl("https://" + deviceName.toLowerCase() + ".com/" + deviceName.toLowerCase());
		ietfMud.setLastUpdate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(Calendar.getInstance().getTime()));
		ietfMud.setCacheValidity(100);
		ietfMud.setSupported(true);
		ietfMud.setSysteminfo(deviceName);
		IetfAccessControlListHolder ietfAccessControlListHolder = new IetfAccessControlListHolder();
		List<AccessControlListHolder> listHolders = new ArrayList<>();
		if (fromIPv4DevicessAccesssListHolder.getAces().getAceList() != null
				&& fromIPv4DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			listHolders.add(fromIPv4DevicessAccesssListHolder);
		}

		if (toIPv4DevicessAccesssListHolder.getAces().getAceList() != null
				&& toIPv4DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			listHolders.add(toIPv4DevicessAccesssListHolder);
		}

		if (fromIPv6DevicessAccesssListHolder.getAces().getAceList() != null
				&& fromIPv6DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			listHolders.add(fromIPv6DevicessAccesssListHolder);
		}

		if (toIPv6DevicessAccesssListHolder.getAces().getAceList() != null
				&& toIPv6DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			listHolders.add(toIPv6DevicessAccesssListHolder);
		}

		ietfAccessControlListHolder.setAccessControlListHolder(listHolders);

		//from DeviceMud
		AccessLists fromAccessLists = new AccessLists();
		AccessList fromAccess = new AccessList();
		List<AccessDTO> fromAccessDTOS = new ArrayList<>();
		if (fromIPv4DevicessAccesssListHolder.getAces().getAceList() != null
				&& fromIPv4DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			fromAccessDTOS.add(fromIpv4AccessDTO);
		}
		if (fromIPv6DevicessAccesssListHolder.getAces().getAceList() != null
				&& fromIPv6DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			fromAccessDTOS.add(fromIpv6AccessDTO);
		}
		fromAccess.setAccessDTOList(fromAccessDTOS);
		fromAccessLists.setAccessList(fromAccess);
		ietfMud.setFromDevicePolicy(fromAccessLists);

		//to DeviceMud
		AccessLists toAccessLists = new AccessLists();
		AccessList toAccess = new AccessList();
		List<AccessDTO> toAccessDTOS = new ArrayList<>();
		if (toIPv4DevicessAccesssListHolder.getAces().getAceList() != null
				&& toIPv4DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			toAccessDTOS.add(toIpv4accessDTO);
		}

		if (toIPv6DevicessAccesssListHolder.getAces().getAceList() != null
				&& toIPv6DevicessAccesssListHolder.getAces().getAceList().size() > 0) {
			toAccessDTOS.add(toIpv6accessDTO);
		}

		toAccess.setAccessDTOList(toAccessDTOS);
		toAccessLists.setAccessList(toAccess);
		ietfMud.setToDevicePolicy(toAccessLists);

		MudSpec mudSpecObj = new MudSpec();
		mudSpecObj.setIetfMud(ietfMud);

		mudSpecObj.setAccessControlList(ietfAccessControlListHolder);
		ObjectMapper mapper = new ObjectMapper();
		String mudSpec = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mudSpecObj);
		mudSpec = mudSpec.replace("\"" + LOCAL_TAG + "\"", "null");
//		System.out.println(mudSpec);
		PrintWriter out = new PrintWriter(currentPath + File.separator + "result"
				+ File.separator + deviceName + File.separator + deviceName + "Mud.json", "UTF-8");
		out.write(mudSpec);
		out.flush();
		out.close();
	}

	private static void printList(List<OFFlow> toPrint, PrintWriter out) {
		for (OFFlow ofFlow : toPrint) {
			ofFlow.setVlanId("NIL");
			String flowString = ofFlow.getFlowStringWithoutFlowStat();
			flowString = flowString.replace(",NIL,", ",");
			out.println(flowString);
		}
		out.flush();
	}

	private static List<Ace> getFromAces(List<OFFlow> fromDevice, String fromId, String defaultGatewayIp, boolean ipv6) {
		List<Ace> aceList = new ArrayList<>();
		int id = 0;
		for (OFFlow ofFlow : fromDevice) {
			if (ipv6 && !ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				continue;
			} else if (!ipv6 && ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				continue;
			}

			Ace ace = new Ace();
			ace.setName(fromId + "-" + id);

			Actions actions = new Actions();
			actions.setForwarding("accept");
			ace.setActions(actions);
			Match match = new Match();

			//L3Match l3Match = new L3Match();
			IPV4Match ipv4Match = null;
			IPV6Match ipv6Match = null;
			if (ofFlow.getIpProto() != null && !ofFlow.getIpProto().equals("*")) {
				ipv4Match = new IPV4Match();
				ipv6Match = new IPV6Match();
				ipv4Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));
				ipv6Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));
			}

			if (!ofFlow.getDstMac().equals(GATEWAYTAG)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				List<String> localString = new ArrayList<>();
				;
				localString.add(LOCAL_TAG);
				ietfMudMatch.setLocalNetworks(localString);
				match.setIetfMudMatch(ietfMudMatch);
				if (validIP(ofFlow.getDstIp())) {
					ipv4Match.setDestinationIp(ofFlow.getDstIp() + "/32");
					if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
						ipv6Match.setDestinationIp(ofFlow.getDstIp());
					}
				}
				if (ofFlow.getDstMac().equals(Constants.BROADCAST_MAC)) {
					EthMatch ethMatch = new EthMatch();
					ethMatch.setEtherType(ofFlow.getEthType());
					ethMatch.setDstMacAddress(ofFlow.getDstMac());
					match.setEthMatch(ethMatch);
				}
			} else if (ofFlow.getDstIp().equals(defaultGatewayIp)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				ietfMudMatch.setController(DEFAULTGATEWAYCONTROLLER);
				match.setIetfMudMatch(ietfMudMatch);
			} else {
				if (validIP(ofFlow.getDstIp())) {
					ipv4Match.setDestinationIp(ofFlow.getDstIp() + "/32");
					if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
						ipv6Match.setDestinationIp(ofFlow.getDstIp());
					}
				} else if (!ofFlow.getDstIp().equals("*")) {
					ipv4Match.setDstDnsName(ofFlow.getDstIp());
					if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
						ipv6Match.setDstDnsName(ofFlow.getDstIp());
					}
				}
			}
			//l3Match.setIpv4Match(ipv4Match);
			if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				match.setIpv6Match(ipv6Match);
			} else {
				match.setIpv4Match(ipv4Match);
			}

			if (ofFlow.getIpProto().equals(Constants.TCP_PROTO)) {
				//L4Match l4Match = new L4Match();
				TcpMatch tcpMatch = new TcpMatch();
				if (ofFlow.getPriority() == Constants.D2G_FIXED_FLOW_INITIALIZED_PRIORITY) {
					tcpMatch.setDirectionInitialized("from-device");
				} else if (ofFlow.getPriority() == Constants.G2D_FIXED_FLOW_INITIALIZED_PRIORITY ||
						ofFlow.getPriority() == Constants.L2D_FIXED_FLOW_INIALIZED_PRIORITY) {
					tcpMatch.setDirectionInitialized("to-device");
				}
				if (!"*".equals(ofFlow.getDstPort())) {
					tcpMatch.setDestinationPortMatch(getPortMatch(ofFlow.getDstPort()));
				} else if (!"*".equals(ofFlow.getSrcPort())) {
					tcpMatch.setSourcePortMatch(getPortMatch(ofFlow.getSrcPort()));
				}
				//l4Match.setTcpMatch(tcpMatch);
				match.setTcpMatch(tcpMatch);

			} else if (ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
				//L4Match l4Match = new L4Match();
				UdpMatch udpMatch = new UdpMatch();
				if (!"*".equals(ofFlow.getDstPort())) {
					udpMatch.setDestinationPortMatch(getPortMatch(ofFlow.getDstPort()));
				} else if (!"*".equals(ofFlow.getSrcPort())) {
					udpMatch.setSourcePortMatch(getPortMatch(ofFlow.getSrcPort()));
				}
				//l4Match.setUdpMatch(udpMatch);
				match.setUdpMatch(udpMatch);
			} else if (ofFlow.getIpProto().equals(Constants.ICMP_PROTO)) {
				IcmpMatch icmpMatch = new IcmpMatch();
				if (!"*".equals(ofFlow.getIcmpCode())) {
					icmpMatch.setCode(Integer.parseInt(ofFlow.getIcmpCode()));
					icmpMatch.setType(Integer.parseInt(ofFlow.getIcmpType()));
					match.setIcmpMatch(icmpMatch);
				}

			}
			ace.setMatches(match);
			aceList.add(ace);
			id++;
		}
		return aceList;
	}

	private static List<Ace> getToAces(List<OFFlow> toDevice, String toId, String defaultGatewayIp, boolean ipv6) {
		List<Ace> aceList = new ArrayList<>();
		int id = 0;
		for (OFFlow ofFlow : toDevice) {
			if (ipv6 && !ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				continue;
			} else if (!ipv6 && ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				continue;
			}
			Ace ace = new Ace();
			ace.setName(toId + "-" + id);
			Actions actions = new Actions();
			actions.setForwarding("accept");
			ace.setActions(actions);
			//L3Match l3Match = new L3Match();
			IPV4Match ipv4Match = null;
			IPV6Match ipv6Match = null;
			if (ofFlow.getIpProto() != null && !ofFlow.getIpProto().equals("*")) {
				ipv4Match = new IPV4Match();
				ipv6Match = new IPV6Match();
				ipv4Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));
				ipv6Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));
			}

			Match match = new Match();
			if (!ofFlow.getSrcMac().equals(GATEWAYTAG)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				List<String> localString = new ArrayList<>();
				;
				localString.add(LOCAL_TAG);
				ietfMudMatch.setLocalNetworks(localString);
				match.setIetfMudMatch(ietfMudMatch);
			} else if (ofFlow.getSrcIp().equals(defaultGatewayIp)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				ietfMudMatch.setController(DEFAULTGATEWAYCONTROLLER);
				match.setIetfMudMatch(ietfMudMatch);
			} else {
				if (validIP(ofFlow.getSrcIp())) {
					ipv4Match.setSourceIp(ofFlow.getSrcIp() + "/32");
					if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
						ipv6Match.setSourceIp(ofFlow.getSrcIp());
					}
				} else if (!ofFlow.getSrcIp().equals("*")) {
					ipv4Match.setSrcDnsName(ofFlow.getSrcIp());
					if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
						ipv6Match.setSrcDnsName(ofFlow.getSrcIp());
					}
				}
			}
			//l3Match.setIpv4Match(ipv4Match);
			if (ofFlow.getEthType().equals(Constants.ETH_TYPE_IPV6)) {
				match.setIpv6Match(ipv6Match);
			} else {
				match.setIpv4Match(ipv4Match);
			}


			if (ofFlow.getIpProto().equals(Constants.TCP_PROTO)) {
				L4Match l4Match = new L4Match();
				TcpMatch tcpMatch = new TcpMatch();
				if (ofFlow.getPriority() == Constants.D2G_FIXED_FLOW_INITIALIZED_PRIORITY) {
					tcpMatch.setDirectionInitialized("from-device");
				} else if (ofFlow.getPriority() == Constants.G2D_FIXED_FLOW_INITIALIZED_PRIORITY ||
						ofFlow.getPriority() == Constants.L2D_FIXED_FLOW_INIALIZED_PRIORITY) {
					tcpMatch.setDirectionInitialized("to-device");
				}
				if (!"*".equals(ofFlow.getSrcPort())) {
					tcpMatch.setSourcePortMatch(getPortMatch(ofFlow.getSrcPort()));
				} else if (!"*".equals(ofFlow.getDstPort())) {
					tcpMatch.setDestinationPortMatch(getPortMatch(ofFlow.getDstPort()));
				}
				//l4Match.setTcpMatch(tcpMatch);
				match.setTcpMatch(tcpMatch);

			} else if (ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
				//L4Match l4Match = new L4Match();
				UdpMatch udpMatch = new UdpMatch();
				if (!"*".equals(ofFlow.getSrcPort())) {
					udpMatch.setSourcePortMatch(getPortMatch(ofFlow.getSrcPort()));
				} else if (!"*".equals(ofFlow.getDstPort())) {
					udpMatch.setDestinationPortMatch(getPortMatch(ofFlow.getDstPort()));
				}
				//l4Match.setUdpMatch(udpMatch);
				match.setUdpMatch(udpMatch);

			} else if (ofFlow.getIpProto().equals(Constants.ICMP_PROTO)) {
				IcmpMatch icmpMatch = new IcmpMatch();
				if (!"*".equals(ofFlow.getIcmpCode())) {
					icmpMatch.setCode(Integer.parseInt(ofFlow.getIcmpCode()));
					icmpMatch.setType(Integer.parseInt(ofFlow.getIcmpType()));
					match.setIcmpMatch(icmpMatch);
				}

			}
			ace.setMatches(match);
			aceList.add(ace);
			id++;
		}
		return aceList;
	}

	private static boolean validIP(String ipAddress) {
		if (ipAddress.equals(Constants.LINK_LOCAL_MULTICAST_IP_RANGE)) {
			return true;
		}
		Matcher m1 = VALID_IPV4_PATTERN.matcher(ipAddress);
		if (m1.matches()) {
			return true;
		}
		Matcher m2 = VALID_IPV6_PATTERN.matcher(ipAddress);
		return m2.matches();
	}

	private static PortMatch getPortMatch(String port) {
		PortMatch portMatch = new PortMatch();
		portMatch.setOperator("eq");
		portMatch.setPort(Integer.parseInt(port));
		return portMatch;
	}


}
