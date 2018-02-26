package com.ayyoob.sdn.of.simulator;


import com.ayyoob.sdn.of.simulator.apps.StatListener;

import java.util.LinkedList;
import java.util.List;

public class OFSwitch {
    private String macAddress;
    private String ip;
    private String dpid;
    private long currentTime=0;
    private long lastPacketTime=0;
    private LinkedList<OFFlow> ofFlows = new LinkedList<OFFlow>();
    private static String ignoreMacPrefix[] = {"01:00:5E", "33:33", "FF:FF:FF"};

    public void transmit(SimPacket packet) {
        currentTime = packet.getTimestamp();
        if (lastPacketTime > currentTime) {
            return;
        }
        cleanIdleFlows();
        for (StatListener statListener : OFController.getInstance().getStatListeners()) {
            statListener.process(dpid, packet);
        }


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

    public OFSwitch(String dpid, String macAddress, String ip) {
        this.dpid = dpid;
        this.macAddress = macAddress.toLowerCase();
        this.ip = ip;
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

            boolean condition = (srcMac.equals(flow.getSrcMac()) || flow.getSrcMac().equals("*"))&&
                    (dstMac.equals(flow.getDstMac())  || flow.getDstMac().equals("*"))&&
                    (ethType.equals(flow.getEthType()) || flow.getEthType().equals("*")) &&
                    (vlanId.equals(flow.getVlanId())  || flow.getVlanId().equals("*"))&&
                    (srcIp.equals(flow.getSrcIp())  || flow.getSrcIp().equals("*"))&&
                    (dstIp.equals(flow.getDstIp())  || flow.getDstIp().equals("*"))&&
                    (ipProto.equals(flow.getIpProto())  || flow.getIpProto().equals("*"))&&
                    (srcPort.equals(flow.getSrcPort())  || flow.getSrcPort().equals("*"))&&
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

    private boolean isIgnored(String mac) {
        for (String prefix : ignoreMacPrefix) {
            if (mac.contains(prefix.toLowerCase())) {
                return true;
            }
        }
        return false;
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
