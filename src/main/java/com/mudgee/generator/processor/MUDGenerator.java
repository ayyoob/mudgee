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

public class MUDGenerator {

	private static final int COMMON_FLOW_PRIORITY = 1000;
	private static final int D2G_PRIORITY = 800;
	private static final int G2D_PRIORITY = 700;
	private static final int L2D_PRIORITY = 600;
	private static final String DEVICETAG = "<deviceMac>";
	private static final String GATEWAYTAG = "<gatewayMac>";
	private static final String DEFAULTGATEWAYCONTROLLER = "urn:ietf:params:mud:gateway";
	private static final String LOCAL_TAG = "localTAG";
	private static final String STUN_PROTO_PORT = "3478";
	private static final int MAX_IP_PER_PROTO = 3;
	// this is used to filter packet that was generated through other means such probing or attacking.
	private static final int MIN_PACKET_COUNT_THRESHOLD = 5;


	public static void generate(String deviceName, String deviceMac, String defaultGatewayIp)
			throws JsonProcessingException, FileNotFoundException, UnsupportedEncodingException {

		String currentPath = Paths.get(".").toAbsolutePath().normalize().toString();
		String workingDirectory = currentPath + File.separator + "result"
				+ File.separator + deviceName  + File.separator;
		File ipflowFile = new File( workingDirectory + deviceMac + "_ipflows.csv");

		IetfMud ietfMud = new IetfMud();
		ietfMud.setMudVersion(1);
		ietfMud.setMudUrl("https://"+ deviceName.toLowerCase() +".com/.well-known/mud/" + deviceName.toLowerCase());
		ietfMud.setLastUpdate(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(Calendar.getInstance().getTime()));
		ietfMud.setCacheValidity(100);
		ietfMud.setSupported(true);
		ietfMud.setSysteminfo(deviceName);
		//from DeviceDTO
		AccessLists fromAccessLists = new AccessLists();
		AccessList fromAccess = new AccessList();
		List<AccessDTO> fromAccessDTOS = new ArrayList<>();
		AccessDTO accessDTO = new AccessDTO();
		accessDTO.setName("from-" + deviceName.toLowerCase().replace(" ", "") );
		fromAccessDTOS.add(accessDTO);
		fromAccess.setAccessDTOList(fromAccessDTOS);
		fromAccessLists.setAccessList(fromAccess);
		ietfMud.setFromDevicePolicy(fromAccessLists);

		MudSpec mudSpecObj = new MudSpec();
		mudSpecObj.setIetfMud(ietfMud);

		//to DeviceDTO
		AccessLists toAccessLists = new AccessLists();
		AccessList toAccess = new AccessList();
		List<AccessDTO> toAccessDTOS = new ArrayList<>();
		AccessDTO toaccessDTO = new AccessDTO();
		toaccessDTO.setName("to-" + deviceName.toLowerCase().replace(" ", "") );
		toAccessDTOS.add(toaccessDTO);
		toAccess.setAccessDTOList(toAccessDTOS);
		toAccessLists.setAccessList(toAccess);
		ietfMud.setToDevicePolicy(toAccessLists);

		List<OFFlow> fromDevice = new ArrayList<>();
		List<OFFlow> toDevice = new ArrayList<>();
		List<OFFlow> localDevice = new ArrayList<>();

		Map<String, OFFlow> commonFlowMap= new HashMap<>();
		Map<String, OFFlow> fromDeviceMap= new HashMap<>();
		Map<String, OFFlow> toDeviceMap= new HashMap<>();

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
					ofFlow.setVolumeTransmitted(Long.parseLong(vals[10]));
					ofFlow.setPacketCount(Long.parseLong(vals[11]));
					if (ofFlow.getPriority() == COMMON_FLOW_PRIORITY|| ofFlow.getPriority() == D2G_PRIORITY
							|| ofFlow.getPriority() == G2D_PRIORITY || ofFlow.getPriority() == L2D_PRIORITY) {
						//ignore
					} else {
						if (ofFlow.getPriority()>COMMON_FLOW_PRIORITY) {
							String key = ofFlow.getIpProto() + "|" + ofFlow.getDstPort() + "|";
							if(validIP(ofFlow.getDstIp()) && ofFlow.getPacketCount() < MIN_PACKET_COUNT_THRESHOLD) {
								continue;
							}
							OFFlow flow = commonFlowMap.get(key);
							if (flow == null) {
								commonFlowMap.put(key, ofFlow);
							} else {
								flow.setDstIp(flow.getDstIp() + "|" + ofFlow.getDstIp());
								commonFlowMap.put(key, flow);
							}
						} else if (ofFlow.getPriority()>D2G_PRIORITY && ofFlow.getPriority() < D2G_PRIORITY + 100) {
							if (validIP(ofFlow.getDstIp()) && ofFlow.getPacketCount() == 0) {
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
							} else if (stunEnabled && ofFlow.getIpProto().equals(Constants.UDP_PROTO) ) {
								continue;
							}
							OFFlow flow = fromDeviceMap.get(key);
							if (flow == null) {
								fromDeviceMap.put(key, ofFlow);
							} else {
								flow.setDstIp(flow.getDstIp() + "|" + ofFlow.getDstIp());
								fromDeviceMap.put(key, flow);
							}
						} else if (ofFlow.getPriority()>G2D_PRIORITY && ofFlow.getPriority() < G2D_PRIORITY + 100) {
							if (validIP(ofFlow.getSrcIp()) && ofFlow.getPacketCount() == 0) {
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
							} else if (stunEnabled && ofFlow.getIpProto().equals(Constants.UDP_PROTO) ) {
								continue;
							}
							OFFlow flow = toDeviceMap.get(key);
							if (flow == null) {
								toDeviceMap.put(key, ofFlow);
							} else {
								flow.setSrcIp(flow.getSrcIp() + "|" + ofFlow.getSrcIp());
								toDeviceMap.put(key, flow);
							}
						} else if (ofFlow.getPriority()>L2D_PRIORITY && ofFlow.getPriority() < L2D_PRIORITY + 100) {
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
			for (String key :commonFlowMap.keySet()) {
				OFFlow ofFlow = commonFlowMap.get(key);
				Set<String> dsts = new HashSet<String>(Arrays.asList(ofFlow.getDstIp().split("\\|")));
				for (String dstLocation : dsts) {
					OFFlow deviceFlow = ofFlow.copy();
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
					toDevice.add(reverseFlow);
				}
			}

			// from device
			for (String key :fromDeviceMap.keySet()) {
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
			for (String key :toDeviceMap.keySet()) {
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
					fromDevice.add(deviceFlow);
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
					+ File.separator + deviceName + File.separator+ deviceName + "rule.csv", "UTF-8");
			ruleOut.println("srcMac,dstMac,ethType,srcIp,dstIp,ipProto,srcPort,dstPort,priority");
			printList(fromDevice, ruleOut);
			printList(toDevice, ruleOut);
			printList(localDevice,ruleOut);
			ruleOut.close();

		} catch (IOException e) {
			e.printStackTrace();
		}

		for(OFFlow ofFlow: localDevice) {
			if (ofFlow.getSrcMac().equals(DEVICETAG)) {
				fromDevice.add(ofFlow);
			} else {
				toDevice.add(ofFlow);
			}
		}

		IetfAccessControlListHolder ietfAccessControlListHolder = new IetfAccessControlListHolder();
		List<AccessControlListHolder> listHolders = new ArrayList<>();
		ietfAccessControlListHolder.setAccessControlListHolder(listHolders);

		//from Device
		AccessControlListHolder fromDevicessAccesssListHolder = new AccessControlListHolder();
		fromDevicessAccesssListHolder.setType("ipv4-acl-type");
		String fromId = "from-" + deviceName.toLowerCase().replace(" ", "");
		fromDevicessAccesssListHolder.setName(fromId);
		ietfAccessControlListHolder.setAccessControlListHolder(listHolders);
		Aces aces = new Aces();
		List<Ace> aceList = new ArrayList<>();
		int id = 0;
		for (OFFlow ofFlow : fromDevice) {
			Ace ace = new Ace();
			ace.setName(fromId + "-" + id);
			Actions actions = new Actions();
			actions.setForwarding("accept");
			ace.setActions(actions);
			Match match = new Match();

			L3Match l3Match = new L3Match();
			IPV4Match ipv4Match = new IPV4Match();
			ipv4Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));

			if (!ofFlow.getDstMac().equals(GATEWAYTAG)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				List<String> localString = new ArrayList<>();;
				localString.add(LOCAL_TAG);
				ietfMudMatch.setLocalNetworks(localString);
				match.setIetfMudMatch(ietfMudMatch);
				if (validIP(ofFlow.getDstIp())) {
					ipv4Match.setDestinationIp(ofFlow.getDstIp() +"/32");
				}
			} else if (ofFlow.getDstIp().equals(defaultGatewayIp)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				ietfMudMatch.setController(DEFAULTGATEWAYCONTROLLER);
				match.setIetfMudMatch(ietfMudMatch);
			} else {
				if (validIP(ofFlow.getDstIp())) {
					ipv4Match.setDestinationIp(ofFlow.getDstIp() +"/32");
				} else if(!ofFlow.getDstIp().equals("*")) {
					ipv4Match.setDstDnsName(ofFlow.getDstIp());
				}
			}
			l3Match.setIpv4Match(ipv4Match);
			match.setL3Match(l3Match);

			if (ofFlow.getIpProto().equals(Constants.TCP_PROTO)) {
				L4Match l4Match = new L4Match();
				TcpMatch tcpMatch = new TcpMatch();
				if (!"*".equals(ofFlow.getDstPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getDstPort()));
					tcpMatch.setDestinationPortMatch(portMatch);
				} else if (!"*".equals(ofFlow.getSrcPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getSrcPort()));
					tcpMatch.setSourcePortMatch(portMatch);
				}
				l4Match.setTcpMatch(tcpMatch);
				match.setL4Match(l4Match);

			} else if (ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
				L4Match l4Match = new L4Match();
				UdpMatch udpMatch = new UdpMatch();
				if (!"*".equals(ofFlow.getDstPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getDstPort()));
					udpMatch.setDestinationPortMatch(portMatch);
				} else if (!"*".equals(ofFlow.getSrcPort())){
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getSrcPort()));
					udpMatch.setSourcePortMatch(portMatch);
				}
				l4Match.setUdpMatch(udpMatch);
				match.setL4Match(l4Match);
			}
			ace.setMatches(match);
			aceList.add(ace);
			id++;
		}
		aces.setAceList(aceList);
		fromDevicessAccesssListHolder.setAces(aces);
		aces.setAceList(aceList);
		fromDevicessAccesssListHolder.setAces(aces);
		listHolders.add(fromDevicessAccesssListHolder);

		//to Device
		AccessControlListHolder toDevicessAccesssListHolder = new AccessControlListHolder();
		toDevicessAccesssListHolder.setType("ipv4-acl-type");
		String toId = "to-" + deviceName.toLowerCase().replace(" ", "");
		toDevicessAccesssListHolder.setName(toId);
		aces = new Aces();
		aceList = new ArrayList<>();
		id = 0;
		for (OFFlow ofFlow : toDevice) {
			Ace ace = new Ace();
			ace.setName(toId + "-" + id);
			Actions actions = new Actions();
			actions.setForwarding("accept");
			ace.setActions(actions);
			L3Match l3Match = new L3Match();
			IPV4Match ipv4Match = new IPV4Match();
			ipv4Match.setProtocol(Integer.parseInt(ofFlow.getIpProto()));
			Match match = new Match();
			if (!ofFlow.getSrcMac().equals(GATEWAYTAG)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				List<String> localString = new ArrayList<>();;
				localString.add(LOCAL_TAG);
				ietfMudMatch.setLocalNetworks(localString);
				match.setIetfMudMatch(ietfMudMatch);
			} else if (ofFlow.getSrcIp().equals(defaultGatewayIp)) {
				IetfMudMatch ietfMudMatch = new IetfMudMatch();
				ietfMudMatch.setController(DEFAULTGATEWAYCONTROLLER);
				match.setIetfMudMatch(ietfMudMatch);
			} else {
				if (validIP(ofFlow.getSrcIp())) {
					ipv4Match.setSourceIp(ofFlow.getSrcIp() +"/32");
				} else if(!ofFlow.getSrcIp().equals("*")){
					ipv4Match.setSrcDnsName(ofFlow.getSrcIp());
				}
			}
			l3Match.setIpv4Match(ipv4Match);
			match.setL3Match(l3Match);

			if (ofFlow.getIpProto().equals(Constants.TCP_PROTO)) {
				L4Match l4Match = new L4Match();
				TcpMatch tcpMatch = new TcpMatch();
				if (!"*".equals(ofFlow.getSrcPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getSrcPort()));
					tcpMatch.setSourcePortMatch(portMatch);
				} else if (!"*".equals(ofFlow.getDstPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getDstPort()));
					tcpMatch.setDestinationPortMatch(portMatch);
				}
				l4Match.setTcpMatch(tcpMatch);
				match.setL4Match(l4Match);

			} else if (ofFlow.getIpProto().equals(Constants.UDP_PROTO)) {
				L4Match l4Match = new L4Match();
				UdpMatch udpMatch = new UdpMatch();
				if (!"*".equals(ofFlow.getSrcPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getSrcPort()));
					udpMatch.setSourcePortMatch(portMatch);
				} else if (!"*".equals(ofFlow.getDstPort())) {
					PortMatch portMatch = new PortMatch();
					portMatch.setOperator("eq");
					portMatch.setPort(Integer.parseInt(ofFlow.getDstPort()));
					udpMatch.setDestinationPortMatch(portMatch);
				}
				l4Match.setUdpMatch(udpMatch);
				match.setL4Match(l4Match);

			}
			ace.setMatches(match);
			aceList.add(ace);
			id++;
		}
		aces.setAceList(aceList);
		toDevicessAccesssListHolder.setAces(aces);
		listHolders.add(toDevicessAccesssListHolder);

		mudSpecObj.setAccessControlList(ietfAccessControlListHolder);
		ObjectMapper mapper = new ObjectMapper();
		String mudSpec = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mudSpecObj);
		mudSpec = mudSpec.replace("\"" + LOCAL_TAG + "\"", "null");
//		System.out.println(mudSpec);
		PrintWriter out = new PrintWriter(currentPath + File.separator + "result"
				+ File.separator + deviceName + File.separator +deviceName+ "Mud.json", "UTF-8");
		out.write(mudSpec);
		out.flush();
		out.close();
	}

	private static void printList(List<OFFlow> toPrint, PrintWriter out) {
		for (OFFlow ofFlow : toPrint) {
			ofFlow.setVlanId("NIL");
			String flowString = ofFlow.getFlowStringWithoutFlowStat();
			flowString= flowString.replace(",NIL,", ",");
			out.println(flowString);
		}
		out.flush();
	}

	private static boolean validIP (String ip) {
		try {
			if ( ip == null || ip.isEmpty() ) {
				return false;
			}
			String[] parts = ip.split( "\\." );
			if ( parts.length != 4 ) {
				return false;
			}
			for ( String s : parts ) {
				int i = Integer.parseInt( s );
				if ( (i < 0) || (i > 255) ) {
					return false;
				}
			}
			if ( ip.endsWith(".") ) {
				return false;
			}
			return true;
		} catch (NumberFormatException nfe) {
			return false;
		}
	}


}
