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

import java.util.LinkedList;
import java.util.List;

public class OFSwitch {
    private String macAddress;
    private String ip;
    private String ipv6;
    private String dpid;
    private long currentTime=0;
    private long lastPacketTime=0;
    private LinkedList<OFFlow> ofFlows = new LinkedList<OFFlow>();

    public void transmit(SimPacket packet) {
        currentTime = packet.getTimestamp();
        if (lastPacketTime > currentTime) {
            return;
        }
        cleanIdleFlows();
        OFFlow flow = getMatchingFlow(packet);
        if (flow.getOfAction() == OFFlow.OFAction.MIRROR_TO_CONTROLLER) {
            flow = getMatchingFlow(packet);
            OFController.getInstance().receive(dpid, packet);
        }
        flow.setVolumeTransmitted(flow.getVolumeTransmitted() + packet.getSize());
        flow.setPacketCount(flow.getPacketCount() + 1);
        flow.setLastPacketTransmittedTime(packet.getTimestamp());
        lastPacketTime = packet.getTimestamp();
    }

    public OFSwitch(String dpid, String macAddress, String ip, String ipv6) {
        this.dpid = dpid;
        this.macAddress = macAddress.toLowerCase();
        this.ip = ip;
        this.ipv6 = ipv6;
        ofFlows.add(getDefaultFlow());
    }

    private OFFlow getDefaultFlow() {
        OFFlow defaultFlow = new OFFlow();
        defaultFlow.setOfAction(OFFlow.OFAction.MIRROR_TO_CONTROLLER);
        defaultFlow.setPriority(1);
        return defaultFlow;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public void setMacAddress(String macAddress) {
        this.macAddress = macAddress;
    }

    public String getIp() {
        return ip;
    }

    public String getIpv6() {
        return ipv6;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getDpid() {
        return dpid;
    }

    public void setDpid(String dpid) {
        this.dpid = dpid;
    }

    public List<OFFlow> getAllFlows() {
        return ofFlows;
    }

    public void addFlow(OFFlow flow){
        boolean exist=false;
        for (int i = 0 ; i < ofFlows.size(); i++) {
            OFFlow currentFlow = ofFlows.get(i);
            if (currentFlow.equals(flow)) {
                exist = true;
            }
        }
        if (!exist) {
            for (int i = 0 ; i < ofFlows.size(); i++) {
                OFFlow currentFlow = ofFlows.get(i);

                if (flow.getPriority() >= currentFlow.getPriority()) {
                    if (i == 0) {
                        ofFlows.addFirst(flow);
                        break;
                    } else {
                        ofFlows.add(i, flow);
                        break;
                    }
                } else if (flow.getPriority() <= 1) {
                    if (currentFlow.equals(getDefaultFlow())) {
                        if (i == 0) {
                            ofFlows.addFirst(flow);
                            break;
                        } else {
                            ofFlows.add(i, flow);
                            break;
                        }
                    }
                }

            }
        }
    }

    public void removeFlow(OFFlow flow) {
        for (int i = 0 ; i < ofFlows.size(); i++) {
            OFFlow currentFlow = ofFlows.get(i);
            if (currentFlow.equals(flow)) {
                ofFlows.remove(i);
            }
        }
    }

    public void clearAllFlows() {
        ofFlows = new LinkedList<OFFlow>();
        ofFlows.add(getDefaultFlow());
        currentTime = 0;
        lastPacketTime = 0;
    }

    private OFFlow getMatchingFlow(SimPacket packet) {
        for (int i = 0 ; i < ofFlows.size(); i++) {
            OFFlow flow = ofFlows.get(i);
            String srcMac=packet.getSrcMac();
            String dstMac=packet.getDstMac();
            String ethType=packet.getEthType();
            String vlanId="*";
            String srcIp=packet.getSrcIp() == null ? "*": packet.getSrcIp();
            String dstIp=packet.getDstIp() == null ? "*": packet.getDstIp();
            String ipProto=packet.getIpProto()== null ? "*": packet.getIpProto();
            String srcPort=packet.getSrcPort()== null ? "*": packet.getSrcPort();
            String dstPort=packet.getDstPort()== null ? "*": packet.getDstPort();
            String icmpType=packet.getIcmpType()== null ? "*": packet.getIcmpType();
            String icmpCode=packet.getIcmpCode()== null ? "*": packet.getIcmpCode();

            boolean condition = (srcMac.equals(flow.getSrcMac()) || flow.getSrcMac().equals("*"))&&
                    (dstMac.equals(flow.getDstMac())  || flow.getDstMac().equals("*"))&&
                    (ethType.equals(flow.getEthType()) || flow.getEthType().equals("*")) &&
                    (vlanId.equals(flow.getVlanId())  || flow.getVlanId().equals("*"))&&
                    (srcIp.equals(flow.getSrcIp())  || flow.getSrcIp().equals("*"))&&
                    (dstIp.equals(flow.getDstIp())  || flow.getDstIp().equals("*"))&&
                    (ipProto.equals(flow.getIpProto())  || flow.getIpProto().equals("*"))&&
                    (srcPort.equals(flow.getSrcPort())  || flow.getSrcPort().equals("*"))&&
                    (icmpType.equals(flow.getIcmpType())  || flow.getIcmpType().equals("*"))&&
                    (icmpCode.equals(flow.getIcmpCode())  || flow.getIcmpCode().equals("*"))&&
                    (dstPort.equals(flow.getDstPort()) || flow.getDstPort().equals("*"));

            if (condition) {
                return flow;
            }
        }
        System.out.println("SOMETHING FISHY .... !!!");
        return ofFlows.getLast();
    }

    public void printFlows() {
        System.out.println(ofFlows.get(0).getFlowHeaderString());
        for (int i = 0 ; i < ofFlows.size(); i++) {
            System.out.println(ofFlows.get(i).getFlowString());
        }
    }


    private void cleanIdleFlows() {
        for (int i = 0 ; i < ofFlows.size(); i++) {
            OFFlow currentFlow = ofFlows.get(i);
            if (currentFlow.getIdleTimeOut() > 0 && (currentTime - currentFlow.getLastPacketTransmittedTime())
                    >= currentFlow.getIdleTimeOut()) {
                ofFlows.remove(i);
            }
        }
    }



}
