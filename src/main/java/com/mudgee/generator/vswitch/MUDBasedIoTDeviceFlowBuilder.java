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

package com.mudgee.generator.vswitch;

import com.mudgee.generator.*;
import com.mudgee.generator.processor.MUDGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.simple.JSONObject;

import java.io.*;
import java.math.BigInteger;
import java.nio.file.Paths;
import java.util.*;

import static com.mudgee.generator.Constants.*;

public class MUDBasedIoTDeviceFlowBuilder implements ControllerApp {

    private static boolean skipIpFlows = false;

    private static final long MAX_FLOWS_PER_DEVICE = 500;
    private static final double MIN_FLOW_IMPACT_THRESHOLD = 5; //percentage
    private static final long MIN_TIME_FOR_FLOWS_MILLI_SECONDS = 120000;

    private boolean enabled = true;
    private List<String> devices;
    private Map<String, Map<String, Set<String>>> deviceDnsMap = new HashMap<>();
    private long startTime = -1;
    private String deviceName;

    public void init(JSONObject jsonObject) {
        String device = (String) jsonObject.get("device");
        deviceName = (String) jsonObject.get("deviceName");
        System.out.println("Generating MUD Profiles for " + deviceName + " for device id" + device);
        devices = new ArrayList<>();
        devices.add(device.toLowerCase());
    }

    public void process(String dpId, SimPacket packet) {
        if (startTime == -1) {
            startTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();

        }
        if (!(devices.contains(packet.getSrcMac()) || devices.contains(packet.getDstMac()))) {
            return;
        }
        if (packet.getEthType().equals(Constants.ETH_TYPE_EAPOL)) {
            OFFlow ofFlow = new OFFlow();
            ofFlow.setEthType(Constants.ETH_TYPE_EAPOL);
            ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
            ofFlow.setPriority(Constants.ALL_DEVICE_COMMON_PRIORITY);
            OFController.getInstance().addFlow(dpId, ofFlow);
        }
        if (isIgnored(packet.getSrcMac()) || isIgnored(packet.getDstMac())) {
            if (isIgnored(packet.getDstMac()) && devices.contains(packet.getSrcMac())) {
                OFFlow ofFlow = new OFFlow();
                ofFlow.setSrcMac(packet.getSrcMac());
                if (packet.getDstMac().equals(Constants.BROADCAST_MAC)) {
                    ofFlow.setDstMac(packet.getDstMac());
                    if (packet.getDstIp() != null && packet.getDstIp().equals(Constants.BROADCAST_IP)) {
                        ofFlow.setDstIp(packet.getDstIp());
                    }
                } else if (packet.getDstIp() != null) {
                    ofFlow.setDstIp(packet.getDstIp());
                }
                ofFlow.setEthType(packet.getEthType());
                if (packet.getDstPort() != null) {
                    ofFlow.setDstPort(packet.getDstPort());
                }
                ofFlow.setIpProto(packet.getIpProto());
                ofFlow.setPriority(Constants.MULTICAST_BROADCAST_PRIORITY);
                ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                OFController.getInstance().addFlow(dpId, ofFlow);
            }
            return;
        }
        String srcMac = packet.getSrcMac();
        String destMac = packet.getDstMac();
        OFSwitch ofSwitch = OFController.getInstance().getSwitch(dpId);
        if (!srcMac.equals(ofSwitch.getMacAddress())) {
            synchronized (ofSwitch) {
                if (getActiveFlows(dpId, srcMac).size() < 10) {
                    initializeDeviceFlows(dpId, srcMac, ofSwitch.getMacAddress());
                }
            }
        }

        if (!destMac.equals(ofSwitch.getMacAddress())) {
            synchronized (ofSwitch) {
                if (getActiveFlows(dpId, destMac).size() < 10) {
                    initializeDeviceFlows(dpId, destMac, ofSwitch.getMacAddress());
                } else {

                }
            }
        }

        if (packet.getSrcMac().equals(ofSwitch.getMacAddress())  && packet.getdnsQname() != null
                && packet.getDnsAnswers() != null && packet.getDnsAnswers().size() > 0) {
            Map<String, Set<String>> dnsMap = deviceDnsMap.get(packet.getDstMac());
            if (dnsMap != null) {
                Set<String> ips = dnsMap.get(packet.getdnsQname());
                if (ips != null) {
                    ips.addAll(packet.getDnsAnswers());
                    dnsMap.put(packet.getdnsQname(), ips);
                } else {
                    ips = new HashSet<>();
                    ips.addAll(packet.getDnsAnswers());
                    dnsMap.put(packet.getdnsQname(), ips);
                }
            } else {
                dnsMap = new HashMap<>();
                Set<String> ips = new HashSet<>();
                ips.addAll(packet.getDnsAnswers());
                dnsMap.put(packet.getdnsQname(), ips);

            }
            deviceDnsMap.put(packet.getDstMac(), dnsMap);
        } else if (packet.getDstMac().equals(ofSwitch.getMacAddress()) && packet.getIpProto()!=null &&
                (packet.getIpProto().equals(Constants.ICMP_PROTO) || packet.getIpProto().equals(Constants.IPV6_ICMP_PROTO))) {
            OFFlow ofFlow = new OFFlow();
            ofFlow.setSrcMac(packet.getSrcMac());
            ofFlow.setDstMac(ofSwitch.getMacAddress());
            ofFlow.setDstIp(packet.getDstIp());
            ofFlow.setIpProto(Constants.ICMP_PROTO);
            ofFlow.setIcmpCode(packet.getIcmpCode());
            ofFlow.setIcmpType(packet.getIcmpType());
            ofFlow.setEthType(packet.getEthType());
            ofFlow.setOfAction(OFFlow.OFAction.NORMAL);

            ofFlow.setPriority(COMMON_FLOW_PRIORITY + 1);
            Set<String> ips = getDnsIps(packet.getSrcMac(), packet.getDstIp());
            if (ips != null) {
                List<OFFlow> deviceFlows = getActiveFlows(dpId, packet.getSrcMac(), COMMON_FLOW_PRIORITY + 1);
                for (OFFlow flow : deviceFlows) {
                    if (flow.getIpProto().equals(Constants.ICMP_PROTO) && flow.getDstMac().equals(ofSwitch.getMacAddress())) {
                        if (ips.contains(flow.getDstIp())) {
                            removeFlow(dpId, flow, packet.getSrcMac());
                        }
                    }
                }
            }

            OFController.getInstance().addFlow(dpId, ofFlow);
        } else if (packet.getDstMac().equals(ofSwitch.getMacAddress()) && packet.getIpProto()!=null && packet.getIpProto().equals(Constants.UDP_PROTO) &&
                (packet.getDstPort().equals(Constants.DNS_PORT) || packet.getDstPort().equals(Constants.NTP_PORT))) {
            OFFlow ofFlow = new OFFlow();
            ofFlow.setSrcMac(packet.getSrcMac());
            ofFlow.setDstMac(ofSwitch.getMacAddress());
            ofFlow.setDstIp(packet.getDstIp());
            ofFlow.setIpProto(Constants.UDP_PROTO);
             ofFlow.setEthType(packet.getEthType());
            ofFlow.setDstPort(packet.getDstPort());
            ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
            ofFlow.setPriority(COMMON_FLOW_PRIORITY + 1);
            Set<String> ips = getDnsIps(packet.getSrcMac(), packet.getDstIp());
            if (ips != null && !packet.getDstPort().equals(Constants.DNS_PORT) ) {
                List<OFFlow> deviceFlows = getActiveFlows(dpId, packet.getSrcMac(), COMMON_FLOW_PRIORITY + 1);
                for (OFFlow flow : deviceFlows) {
                    if (flow.getIpProto().equals(Constants.UDP_PROTO) && flow.getDstMac().equals(ofSwitch.getMacAddress()) &&
                            packet.getDstPort().equals(Constants.NTP_PORT)) {
                        if (ips.contains(flow.getDstIp())) {
                            long flowInitializedTime = flow.getCreatedTimestamp();
                            long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                            long age = currentTime - flowInitializedTime;
                            if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                removeFlow(dpId, flow, packet.getSrcMac());
                            }
                        }
                    }
                }
            }

            OFController.getInstance().addFlow(dpId, ofFlow);
        } else if (packet.getIpProto()!=null) {
            String dstIp = packet.getDstIp();
            String srcIp = packet.getSrcIp();
            String protocol = packet.getIpProto();
            String srcPort = packet.getSrcPort();
            String dstPort = packet.getDstPort();

            //Only UDP and TCP proto
            if (protocol != null && (protocol.equals(Constants.TCP_PROTO) || protocol.equals(Constants.UDP_PROTO))) {
                // Device 2 Gateway flow
                if (destMac.equals(ofSwitch.getMacAddress()) && Integer.parseInt(dstPort) != 53
                        && Integer.parseInt(dstPort) != 123) {
                    String deviceMac = srcMac;

                    if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN) {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setDstIp(packet.getDstIp());
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                        ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(D2G_FIXED_FLOW_INITIALIZED_PRIORITY);
                        Set<String> ips = getDnsIps(deviceMac, packet.getDstIp());
                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
///                        priorities.add(D2G_FIXED_FLOW_PRIORITY);
                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getDstIp());
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setSrcPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(G2D_FIXED_FLOW_PRIORITY);

                        priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);
                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getSrcIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        OFController.getInstance().addFlow(dpId, ofFlow);
                    } else if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN_ACK) {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setDstIp(packet.getDstIp());
                        ofFlow.setSrcPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(D2G_FIXED_FLOW_PRIORITY);
                        Set<String> ips = getDnsIps(deviceMac, packet.getDstIp());
                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(D2G_FIXED_FLOW_PRIORITY);
                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(srcPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getDstIp());
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(G2D_FIXED_FLOW_INITIALIZED_PRIORITY);

                        priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);
                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);

                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(srcPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getSrcIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);
                    } else {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setDstIp(packet.getDstIp());
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(D2G_DYNAMIC_FLOW_PRIORITY);

                        Set<String> ips = getDnsIps(deviceMac, packet.getDstIp());

                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(D2G_FIXED_FLOW_PRIORITY);
                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        addFlow(dpId, ofFlow, deviceMac);

                        ofFlow = new OFFlow();
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getDstIp());
                        ofFlow.setSrcPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(G2D_DYNAMIC_FLOW_PRIORITY);

                        priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);
                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        addFlow(dpId, ofFlow, deviceMac);
                    }
                    // Gateway to Device
                } else if (srcMac.equals(ofSwitch.getMacAddress()) && Integer.parseInt(srcPort) != 53
                        && Integer.parseInt(srcPort) != 123) {
                    String deviceMac = destMac;
                    if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN) {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getSrcIp());
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(G2D_FIXED_FLOW_INITIALIZED_PRIORITY);
                        Set<String> ips = getDnsIps(deviceMac, packet.getSrcIp());
                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);

                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getSrcIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setDstIp(packet.getSrcIp());
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(D2G_FIXED_FLOW_PRIORITY);
                        priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(D2G_FIXED_FLOW_PRIORITY);

                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);
                    } else if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN_ACK) {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getSrcIp());
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setSrcPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(G2D_FIXED_FLOW_PRIORITY);

                        Set<String> ips = getDnsIps(deviceMac, packet.getSrcIp());
                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);

                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(srcPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getSrcIp()) && age >MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setDstIp(packet.getSrcIp());
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setDstPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(D2G_FIXED_FLOW_INITIALIZED_PRIORITY);

                        priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);

                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);

                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(srcPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age >MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);
                    } else {
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setSrcMac(ofSwitch.getMacAddress());
                        ofFlow.setSrcIp(packet.getSrcIp());
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setPriority(G2D_DYNAMIC_FLOW_PRIORITY);
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);

                        List<Integer> priorities = new ArrayList<>();
                        priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(G2D_FIXED_FLOW_PRIORITY);
                        List<OFFlow> deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        Set<String> ips = getDnsIps(deviceMac, packet.getSrcIp());
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getSrcIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }

                        addFlow(dpId, ofFlow, deviceMac);

                        ofFlow = new OFFlow();
                        ofFlow.setDstMac(ofSwitch.getMacAddress());
                        ofFlow.setDstIp(packet.getSrcIp());
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setSrcPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setPriority(D2G_DYNAMIC_FLOW_PRIORITY);
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);

                        priorities = new ArrayList<>();
                        priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
///                         priorities.add(D2G_FIXED_FLOW_PRIORITY);

                        deviceFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                        if (ips != null) {
                            for (OFFlow flow : deviceFlows) {
                                if (flow.getIpProto().equals(protocol) && flow.getSrcPort().equals(dstPort)) {
                                    long flowInitializedTime = flow.getCreatedTimestamp();
                                    long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                                    long age = currentTime - flowInitializedTime;
                                    if (ips.contains(flow.getDstIp()) && age > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                                        removeFlow(dpId, flow, deviceMac);
                                    }
                                }
                            }
                        }


                        addFlow(dpId, ofFlow, deviceMac);
                    }
                    //
                } else if ((!destMac.equals(ofSwitch.getMacAddress())) && !isIgnored(destMac)) {

                    if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN) {
                        String deviceMac = destMac;
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(L2D_FIXED_FLOW_INIALIZED_PRIORITY);

                        List<OFFlow> deviceFlows = getActiveFlows(dpId, deviceMac, L2D_DYNAMIC_FLOW_PRIORITY);
                        for (OFFlow flow : deviceFlows) {
                            if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(dstPort)) {
                                removeFlow(dpId, flow, deviceMac);
                            }
                        }

                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setSrcPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(L2D_FIXED_FLOW_PRIORITY);
                        OFController.getInstance().addFlow(dpId, ofFlow);
                    } else if (protocol.equals(Constants.TCP_PROTO) && packet.getTcpFlag() == SimPacket.Flag.SYN_ACK) {
                        String deviceMac = srcMac;

                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(L2D_FIXED_FLOW_PRIORITY);

                        List<OFFlow> deviceFlows = getActiveFlows(dpId, deviceMac, L2D_DYNAMIC_FLOW_PRIORITY);
                        for (OFFlow flow : deviceFlows) {
                            if (flow.getIpProto().equals(protocol) && flow.getDstPort().equals(srcPort)) {
                                removeFlow(dpId, flow, deviceMac);
                            }
                        }
                        OFController.getInstance().addFlow(dpId, ofFlow);


                        ofFlow = new OFFlow();
                        ofFlow.setSrcMac(deviceMac);
                        ofFlow.setSrcPort(srcPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        ofFlow.setPriority(L2D_FIXED_FLOW_INIALIZED_PRIORITY);
                        OFController.getInstance().addFlow(dpId, ofFlow);

                    } else {
                        String deviceMac = destMac;
                        OFFlow ofFlow = new OFFlow();
                        ofFlow.setDstMac(deviceMac);
                        ofFlow.setDstPort(dstPort);
                        ofFlow.setIpProto(protocol);
                         ofFlow.setEthType(packet.getEthType());
                        ofFlow.setPriority(L2D_DYNAMIC_FLOW_PRIORITY);
                        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
                        addFlow(dpId, ofFlow, deviceMac);
                    }
                }
            }
        }
    }

    private void addFlow(String dpId, OFFlow ofFlow, String deviceMac) {
        List<OFFlow> deviceFlows = getActiveFlows(dpId, deviceMac, ofFlow.getPriority());
        if (deviceFlows.size() < MAX_FLOWS_PER_DEVICE) {
            ofFlow.setCreatedTimestamp(OFController.getInstance().getSwitch(dpId).getCurrentTime());
            OFController.getInstance().addFlow(dpId, ofFlow);
        } else {
            OFFlow tobeRemoved = null;
            double packetCountRateForPriorityLevel = getTotalPacketCountRate(dpId, deviceMac, ofFlow.getPriority());
            int flowsConsideredForRemoval = 0;
            for (OFFlow flow : deviceFlows) {

                long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                long flowInitializedTime = flow.getCreatedTimestamp();
                long age = currentTime - flowInitializedTime;
                if (currentTime - flowInitializedTime > MIN_TIME_FOR_FLOWS_MILLI_SECONDS) {
                    if (tobeRemoved == null) {
                        Double flowImpact = ((flow.getPacketCount() * 1.0)/age) / packetCountRateForPriorityLevel;
                        if (MIN_FLOW_IMPACT_THRESHOLD > (flowImpact * 100)) {
                            tobeRemoved = flow;
                            flowsConsideredForRemoval++;
                        }
                    } else {
                        flowsConsideredForRemoval++;
                        Double flowImpact = ((flow.getPacketCount() * 1.0)/age) / packetCountRateForPriorityLevel;
                        Double tobeRemovedFlowImpact = ((tobeRemoved.getPacketCount() * 1.0)/age) / packetCountRateForPriorityLevel;
                        if (tobeRemovedFlowImpact > flowImpact) {
                            tobeRemoved = flow;
                        }
                    }
                }
            }
            if (tobeRemoved != null && flowsConsideredForRemoval >= 1) {
                removeFlow(dpId, tobeRemoved, deviceMac);
                OFController.getInstance().addFlow(dpId, ofFlow);
            }

        }
    }

    private void removeFlow(String dpId, OFFlow ofFlow, String deviceMac) {
//        deviceFlowTimeMap.remove(ofFlow);
//        long count[] = deviceFlowPacketCountBeenRemoved.get(deviceMac);
//        if (ofFlow.getPriority() == D2G_DYNAMIC_FLOW_PRIORITY) {
//            count[0] = count[0] + ofFlow.getPacketCount();
//        } else if (ofFlow.getPriority() == G2D_DYNAMIC_FLOW_PRIORITY) {
//            count[1] = count[1] + ofFlow.getPacketCount();
//        } else if (ofFlow.getPriority() == L2D_DYNAMIC_FLOW_PRIORITY) {
//            count[2] = count[2] + ofFlow.getPacketCount();
//        }
//        deviceFlowPacketCountBeenRemoved.put(deviceMac, count);
        OFController.getInstance().removeFlow(dpId, ofFlow);
    }


    public List<OFFlow> getActiveFlows(String dpId, String deviceMac) {
        List<OFFlow> flowList = OFController.getInstance().getAllFlows(dpId);
        List<OFFlow> deviceFlowList = new ArrayList<OFFlow>();
        for (OFFlow ofFlow : flowList) {
            if (ofFlow.getSrcMac().equals(deviceMac) || ofFlow.getDstMac().equals(deviceMac)) {
                deviceFlowList.add(ofFlow);
            }
        }
        return deviceFlowList;
    }

    public List<OFFlow> getActiveFlows(String dpId, String deviceMac, int priority) {
        List<OFFlow> flowList = OFController.getInstance().getAllFlows(dpId);
        List<OFFlow> deviceFlowList = new ArrayList<OFFlow>();
        for (OFFlow ofFlow : flowList) {
            if (ofFlow.getSrcMac().equals(deviceMac) || ofFlow.getDstMac().equals(deviceMac)) {
                if (ofFlow.getPriority() == priority) {
                    deviceFlowList.add(ofFlow);
                }
            }
        }
        return deviceFlowList;
    }

    public List<OFFlow> getActiveFlowsForPriorities(String dpId, String deviceMac, List<Integer> priority) {
        List<OFFlow> flowList = OFController.getInstance().getAllFlows(dpId);
        List<OFFlow> deviceFlowList = new ArrayList<OFFlow>();
        for (OFFlow ofFlow : flowList) {
            if (ofFlow.getSrcMac().equals(deviceMac) || ofFlow.getDstMac().equals(deviceMac)) {
                if (priority.contains(ofFlow.getPriority())) {
                    deviceFlowList.add(ofFlow);
                }
            }
        }
        return deviceFlowList;
    }

    public double getTotalPacketCountRate(String dpId, String deviceMac, int priority) {
        double totalPacketCount = 0;

        List<OFFlow> deviceFlows = getActiveFlows(dpId, deviceMac);
        for (OFFlow ofFlow : deviceFlows) {
            if (ofFlow.getPriority() == priority || ofFlow.getPriority() == (priority - 10) || ofFlow.getPriority() == (priority + 40)) {
                totalPacketCount = totalPacketCount +( (ofFlow.getPacketCount()*1.0)/
                        (OFController.getInstance().getSwitch(dpId).getCurrentTime()- ofFlow.getCreatedTimestamp()));
            }
        }

        return totalPacketCount;
    }

    private boolean isIgnored(String mac) {
        if (mac.length() == Constants.BROADCAST_MAC.length()) {
            String mostSignificantByte = mac.split(":")[0];
            String binary = new BigInteger(mostSignificantByte, 16).toString(2);
            if (mac.equals(Constants.BROADCAST_MAC) || binary.charAt(binary.length() -1) == '1') {
                return true;
            }
        }
        return false;
    }

    public void initializeDeviceFlows(String dpId, String deviceMac, String gwMac) {
        if (isIgnored(deviceMac)) {
            return;
        }
        //SKIP GATEWAY
        OFFlow ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setDstIp(OFController.getInstance().getSwitch(dpId).getIp());
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(SKIP_FLOW_HIGHER_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setSrcMac(gwMac);
        ofFlow.setSrcIp(OFController.getInstance().getSwitch(dpId).getIp());
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(SKIP_FLOW_HIGHER_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setDstIp(OFController.getInstance().getSwitch(dpId).getIpv6());
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(SKIP_FLOW_HIGHER_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setSrcMac(gwMac);
        ofFlow.setSrcIp(OFController.getInstance().getSwitch(dpId).getIpv6());
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(SKIP_FLOW_HIGHER_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        //DNS
        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setEthType(Constants.ETH_TYPE_ARP);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setEthType(Constants.ETH_TYPE_ARP);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setDstPort(Constants.DNS_PORT);
        //ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setSrcPort(Constants.DNS_PORT);
        //ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        //NTP
        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setDstPort(Constants.NTP_PORT);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setSrcPort(Constants.NTP_PORT);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        //ICMP
        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.IPV6_ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.IPV6_ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(COMMON_FLOW_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);


        //Device -> GW
        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.TCP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(D2G_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(deviceMac);
        ofFlow.setDstMac(gwMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(D2G_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        //GW - > Device

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.TCP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(G2D_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setSrcMac(gwMac);
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(G2D_PRIORITY);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        //Local
        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(L2D_PRIORITY);
        ofFlow.setIpProto(Constants.TCP_PROTO);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(L2D_PRIORITY);
        ofFlow.setIpProto(Constants.UDP_PROTO);
        ofFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        OFController.getInstance().addFlow(dpId, ofFlow);

//        ofFlow = new OFFlow();
//        ofFlow.setSrcMac(deviceMac);
//        ofFlow.setDstIp("239.255.255.250");
//        ofFlow.setDstPort("1900");
//        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
//        ofFlow.setPriority(L2D_PRIORITY + 5);
//        ofFlow.setIpProto(Constants.UDP_PROTO);
//        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
//        OFController.getInstance().addFlow(dpId, ofFlow);
//
//        ofFlow = new OFFlow();
//        ofFlow.setSrcMac(deviceMac);
//        ofFlow.setDstIp("239.255.255.250");
//        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
//        ofFlow.setPriority(L2D_PRIORITY + 5);
//        ofFlow.setIpProto(Constants.IGMP_PROTO);
//        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
//        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV4);
        ofFlow.setPriority(L2D_PRIORITY + 1);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);

        ofFlow = new OFFlow();
        ofFlow.setDstMac(deviceMac);
        ofFlow.setIpProto(Constants.ICMP_PROTO);
        ofFlow.setEthType(Constants.ETH_TYPE_IPV6);
        ofFlow.setPriority(L2D_PRIORITY + 1);
        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
        OFController.getInstance().addFlow(dpId, ofFlow);


//        ofFlow = new OFFlow();
//        ofFlow.setDstMac(deviceMac);
//        ofFlow.setPriority(SKIP_FLOW_PRIORITY);
//        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
//        OFController.getInstance().addFlow(dpId, ofFlow);
//
//        ofFlow = new OFFlow();
//        ofFlow.setSrcMac(deviceMac);
//        ofFlow.setPriority(SKIP_FLOW_PRIORITY);
//        ofFlow.setOfAction(OFFlow.OFAction.NORMAL);
//        OFController.getInstance().addFlow(dpId, ofFlow);
    }

    public void complete() {
        if (!enabled) {
            return;
        }
        Set<String> switchMap = OFController.getInstance().getSwitchIds();
        for (String dpId : switchMap) {
            List<OFFlow> flowsTobeRemoved = new ArrayList<OFFlow>();
            List<OFFlow> flowsTobeAdded = new ArrayList<>();
            for (String deviceMac : devices) {
                double totalPacketCountRate = getTotalPacketCountRate(dpId, deviceMac, D2G_DYNAMIC_FLOW_PRIORITY);
                List<Integer> priorities = new ArrayList<>();
                priorities.add(D2G_DYNAMIC_FLOW_PRIORITY);
                //priorities.add(D2G_FIXED_FLOW_PRIORITY);
                List<OFFlow> ofFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                long currentTime = OFController.getInstance().getSwitch(dpId).getCurrentTime();
                for (OFFlow ofFlow : ofFlows) {
                    long flowInitializedTime = ofFlow.getCreatedTimestamp();
                    long age = currentTime - flowInitializedTime;
                    Double flowImpact = ((ofFlow.getPacketCount() * 1.0)/age) / totalPacketCountRate;
                    if ((flowImpact * 100) < MIN_FLOW_IMPACT_THRESHOLD) {
                        flowsTobeRemoved.add(ofFlow);
                        if (ofFlow.getPriority() == D2G_FIXED_FLOW_PRIORITY) {
                            OFFlow reverseFlow = ofFlow.copy();
                            reverseFlow.setSrcMac(ofFlow.getDstMac());
                            reverseFlow.setDstMac(ofFlow.getSrcMac());
                            reverseFlow.setSrcIp(ofFlow.getDstIp());
                            reverseFlow.setDstIp(ofFlow.getSrcIp());
                            reverseFlow.setSrcPort(ofFlow.getDstPort());
                            reverseFlow.setDstPort(ofFlow.getSrcPort());
                            flowsTobeRemoved.add(reverseFlow);
                        }
                    } else {

                        OFFlow reverseFlow = ofFlow.copy();
                        reverseFlow.setSrcMac(ofFlow.getDstMac());
                        reverseFlow.setDstMac(ofFlow.getSrcMac());
                        reverseFlow.setSrcIp(ofFlow.getDstIp());
                        reverseFlow.setDstIp(ofFlow.getSrcIp());
                        reverseFlow.setSrcPort(ofFlow.getDstPort());
                        reverseFlow.setDstPort(ofFlow.getSrcPort());
                        reverseFlow.setPriority(G2D_DYNAMIC_FLOW_PRIORITY);
                        reverseFlow.setPacketCount(ofFlow.getPacketCount());
                        reverseFlow.setVolumeTransmitted(ofFlow.getVolumeTransmitted());
                        flowsTobeAdded.add(reverseFlow);
                    }
                }


                totalPacketCountRate = getTotalPacketCountRate(dpId, deviceMac, G2D_DYNAMIC_FLOW_PRIORITY);
                priorities = new ArrayList<>();
                priorities.add(G2D_DYNAMIC_FLOW_PRIORITY);
                //priorities.add(G2D_FIXED_FLOW_PRIORITY);
                ofFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                for (OFFlow ofFlow : ofFlows) {
                    long flowInitializedTime = ofFlow.getCreatedTimestamp();
                    long age = currentTime - flowInitializedTime;
                    Double flowImpact = ((ofFlow.getPacketCount() * 1.0)/age) / totalPacketCountRate;
                    if ((flowImpact * 100) < MIN_FLOW_IMPACT_THRESHOLD) {
                        flowsTobeRemoved.add(ofFlow);
                        if (ofFlow.getPriority() == G2D_FIXED_FLOW_PRIORITY) {
                            OFFlow reverseFlow = ofFlow.copy();
                            reverseFlow.setSrcMac(ofFlow.getDstMac());
                            reverseFlow.setDstMac(ofFlow.getSrcMac());
                            reverseFlow.setSrcIp(ofFlow.getDstIp());
                            reverseFlow.setDstIp(ofFlow.getSrcIp());
                            reverseFlow.setSrcPort(ofFlow.getDstPort());
                            reverseFlow.setDstPort(ofFlow.getSrcPort());
                            flowsTobeRemoved.add(reverseFlow);
                        }
                    } else {
                        OFFlow reverseFlow = ofFlow.copy();
                        reverseFlow.setSrcMac(ofFlow.getDstMac());
                        reverseFlow.setDstMac(ofFlow.getSrcMac());
                        reverseFlow.setSrcIp(ofFlow.getDstIp());
                        reverseFlow.setDstIp(ofFlow.getSrcIp());
                        reverseFlow.setSrcPort(ofFlow.getDstPort());
                        reverseFlow.setDstPort(ofFlow.getSrcPort());
                        reverseFlow.setPriority(D2G_DYNAMIC_FLOW_PRIORITY);
                        reverseFlow.setPacketCount(ofFlow.getPacketCount());
                        reverseFlow.setVolumeTransmitted(ofFlow.getVolumeTransmitted());
                        flowsTobeAdded.add(reverseFlow);
                    }
                }

                totalPacketCountRate = getTotalPacketCountRate(dpId, deviceMac, L2D_DYNAMIC_FLOW_PRIORITY);
                priorities = new ArrayList<>();
                priorities.add(L2D_DYNAMIC_FLOW_PRIORITY);
                //priorities.add(L2D_FIXED_FLOW_PRIORITY);
                ofFlows = getActiveFlowsForPriorities(dpId, deviceMac, priorities);
                for (OFFlow ofFlow : ofFlows) {
                    long flowInitializedTime = ofFlow.getCreatedTimestamp();
                    long age = currentTime - flowInitializedTime;
                    Double flowImpact = ((ofFlow.getPacketCount() * 1.0)/age) / totalPacketCountRate;
                    if ((flowImpact * 100) < MIN_FLOW_IMPACT_THRESHOLD) {
                        flowsTobeRemoved.add(ofFlow);
                        if (ofFlow.getPriority() == L2D_FIXED_FLOW_PRIORITY) {
                            OFFlow reverseFlow = ofFlow.copy();
                            reverseFlow.setSrcMac(ofFlow.getDstMac());
                            reverseFlow.setDstMac(ofFlow.getSrcMac());
                            reverseFlow.setSrcIp(ofFlow.getDstIp());
                            reverseFlow.setDstIp(ofFlow.getSrcIp());
                            reverseFlow.setSrcPort(ofFlow.getDstPort());
                            reverseFlow.setDstPort(ofFlow.getSrcPort());
                            flowsTobeRemoved.add(reverseFlow);
                        }
                    } else {
                        OFFlow reverseFlow = ofFlow.copy();
                        reverseFlow.setDstMac(ofFlow.getSrcMac());
                        reverseFlow.setSrcMac(ofFlow.getDstMac());
                        reverseFlow.setSrcIp(ofFlow.getDstIp());
                        reverseFlow.setDstIp(ofFlow.getSrcIp());
                        reverseFlow.setSrcPort(ofFlow.getDstPort());
                        reverseFlow.setDstPort(ofFlow.getSrcPort());
                        reverseFlow.setPacketCount(ofFlow.getPacketCount());
                        reverseFlow.setVolumeTransmitted(ofFlow.getVolumeTransmitted());
                        flowsTobeAdded.add(reverseFlow);
                    }
                }
                for (OFFlow ofFlow : flowsTobeRemoved) {
                    removeFlow(dpId, ofFlow, deviceMac);
                }
                for (OFFlow ofFlow : flowsTobeAdded) {
                    OFController.getInstance().addFlow(dpId, ofFlow);
                }
                String currentPath = Paths.get(".").toAbsolutePath().normalize().toString();

                File workingDirectory = new File(currentPath + File.separator + "result");
                if (!workingDirectory.exists()) {
                    workingDirectory.mkdir();
                }
                workingDirectory = new File(currentPath + File.separator + "result" + File.separator + deviceName);
                if (!workingDirectory.exists()) {
                    workingDirectory.mkdir();
                }
                currentPath = currentPath + File.separator + "result" + File.separator + deviceName;
                ofFlows = getActiveFlows(dpId, deviceMac);
                Map<String, Set<String>> dnsMap = deviceDnsMap.get(deviceMac);
                if (dnsMap != null) {
                    for (OFFlow ofFlow : ofFlows) {
                        String srcDns = getDnsForIp(dnsMap, ofFlow.getSrcIp());
                        if (srcDns.length() != 0) {
                            ofFlow.setSrcIp(srcDns);
                        }
                        String dstDns = getDnsForIp(dnsMap, ofFlow.getDstIp());
                        if (dstDns.length() != 0) {
                            ofFlow.setDstIp(dstDns);
                        }
                    }
                }
                System.out.println("Please find the generated file in path: " + currentPath);
                PrintWriter writer = null;
                if (!skipIpFlows) {
                    try {
                        writer = new PrintWriter(currentPath + File.separator + deviceMac + "_ipflows.csv", "UTF-8");
                        Set<OFFlow> exist = new HashSet<OFFlow>();

                        if (ofFlows.size() > 0) {
                            System.out.println("Device : " + deviceMac);
                            boolean first = true;
                            for (OFFlow ofFlow : ofFlows) {
                                if (ofFlow.getPriority() == SKIP_FLOW_HIGHER_PRIORITY || ofFlow.getPriority() == SKIP_FLOW_PRIORITY) {
                                    continue;
                                }
                                if (first) {
                                    //System.out.println(ofFlow.getFlowHeaderString());
                                    writer.println(ofFlow.getFlowHeaderString());
                                    first = false;
                                }
                                //System.out.println(ofFlow.getFlowString());
                                writer.println(ofFlow.getFlowString());
                                exist.add(ofFlow);
                            }
                        }
                    } catch (FileNotFoundException | UnsupportedEncodingException e) {
                        e.printStackTrace();
                    } finally {
                        if (writer != null) {
                            writer.close();
                        }
                    }
                }
                try {
                    MUDGenerator.generate(deviceName, deviceMac, OFController.getInstance().getSwitch(dpId).getIp());
                } catch (JsonProcessingException |FileNotFoundException |UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                System.out.println("startTime: " + startTime);
                System.out.println("EndTime: " + OFController.getInstance().getSwitch(dpId).getCurrentTime());
                System.out.println("Runtime: " + (OFController.getInstance().getSwitch(dpId).getCurrentTime() - startTime)/1000);


            }
        }


    }

    private Set<String> getDnsIps(String deviceMac, String ip) {
        Map<String, Set<String>> dnsMap = deviceDnsMap.get(deviceMac);
        if(dnsMap != null ) {
            for (String key : dnsMap.keySet()) {
                if(dnsMap.get(key).contains(ip)) {
                    return dnsMap.get(key);
                }
            }
        }
        return null;
    }

    String getDnsForIp(Map<String, Set<String>> dnsMap, String ip) {
        String ips = "";
        for (String dns : dnsMap.keySet()) {
            if (dnsMap.get(dns).contains(ip)) {
                if (ips.length() ==0) {
                    ips = dns;
                } else {
                    ips = ips + "|" + dns;
                }
            }
        }
        return ips;
    }

}
