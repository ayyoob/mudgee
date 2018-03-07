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

package com.mudgee.generator;

import com.mudgee.generator.vswitch.*;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.pcap4j.core.NotOpenException;
import org.pcap4j.core.PcapHandle;
import org.pcap4j.core.PcapNativeException;
import org.pcap4j.core.Pcaps;
import org.pcap4j.packet.*;
import org.pcap4j.packet.namednumber.EtherType;
import org.pcap4j.packet.namednumber.IpNumber;

import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * This tools simulates the PCAP through a virtual switch to identify the most common device flow rules.
 */
public class Mudgee {

    public static void main(String[] args) throws Exception {

        System.out.println("Working Directory is set to:" + Paths.get(".").toAbsolutePath().normalize().toString());

        JSONParser parser = new JSONParser();
        ClassLoader classLoader = Mudgee.class.getClassLoader();
        File file;
        if (args != null && args.length > 0&& args[0] != null && args[0].length() >0) {
            file = new File(args[0]);
        } else {
            file = new File(classLoader.getResource("apps/mud_config.json").getFile());
        }
        Object obj = parser.parse(new FileReader(file));

        JSONObject jsonObject = (JSONObject) obj;

        String pcapLocation = (String) jsonObject.get("pcapLocation");
        JSONObject switchConfig = (JSONObject) jsonObject.get("defaultGatewayConfig");
        String dpId = (String) switchConfig.get("macAddress");
        String macAddress = (String) switchConfig.get("macAddress");
        String ipAddress = (String) switchConfig.get("ipAddress");

        JSONObject deviceConfig = (JSONObject) jsonObject.get("deviceConfig");
        OFController.getInstance().registerApps(new MUDBasedIoTDeviceFlowBuilder(), deviceConfig);
        final OFSwitch ofSwitch = new OFSwitch(dpId, macAddress, ipAddress);
        OFController.getInstance().addSwitch(ofSwitch);
        processPcap(pcapLocation, ofSwitch);
        OFController.getInstance().complete();
    }

    private static void processPcap(String pcapLocation, OFSwitch ofSwitch) throws PcapNativeException {
        boolean firstPacket = false;
        long startTimestamp = 0;
        long endTimestamp= 0;
        long totalPacketCount=0;
        long sumPacketProcessingTime=0;

        PcapHandle handle;
        try {
            handle = Pcaps.openOffline(pcapLocation, PcapHandle.TimestampPrecision.NANO);
        } catch (PcapNativeException e) {
            handle = Pcaps.openOffline(pcapLocation);
        }
        try {
            while (true) {
                Packet packet;
                try {
                    packet = handle.getNextPacketEx();
                } catch (IllegalArgumentException e) {
                    continue;
                }

                totalPacketCount++;
                //System.out.println(packet);
                SimPacket simPacket = new SimPacket();
                if (!firstPacket) {
                    startTimestamp = handle.getTimestamp().getTime();
                    firstPacket=true;
                }

                endTimestamp =handle.getTimestamp().getTime();
                simPacket.setTimestamp(handle.getTimestamp().getTime());
                try {
                    EthernetPacket.EthernetHeader header = (EthernetPacket.EthernetHeader) packet.getHeader();
                    if (header == null) {
                        continue;
                    }
                    simPacket.setSrcMac(header.getSrcAddr().toString());
                    simPacket.setDstMac(header.getDstAddr().toString());
                    simPacket.setSize(packet.length());
                    simPacket.setEthType(header.getType().valueAsString());
                    if (header.getType() == EtherType.IPV4) {
                        IpV4Packet ipV4Packet = (IpV4Packet) packet.getPayload();
                        IpV4Packet.IpV4Header ipV4Header = ipV4Packet.getHeader();
                        simPacket.setSrcIp(ipV4Header.getSrcAddr().getHostAddress());
                        simPacket.setDstIp(ipV4Header.getDstAddr().getHostAddress());
                        simPacket.setIpProto(ipV4Header.getProtocol().valueAsString());
                        if (ipV4Header.getProtocol().valueAsString().equals(IpNumber.TCP.valueAsString()) ) {
                            TcpPacket tcpPacket = (TcpPacket) ipV4Packet.getPayload();
                            simPacket.setSrcPort(tcpPacket.getHeader().getSrcPort().valueAsString());
                            simPacket.setDstPort(tcpPacket.getHeader().getDstPort().valueAsString());
                            simPacket.setTcpFlag(tcpPacket.getHeader().getSyn(),tcpPacket.getHeader().getAck());

                        } else if (ipV4Header.getProtocol().valueAsString().equals(IpNumber.UDP.valueAsString()) ) {
                            UdpPacket udpPacket = (UdpPacket) ipV4Packet.getPayload();
                            simPacket.setSrcPort(udpPacket.getHeader().getSrcPort().valueAsString());
                            simPacket.setDstPort(udpPacket.getHeader().getDstPort().valueAsString());

                            if (udpPacket.getHeader().getDstPort().valueAsString().equals(Constants.DNS_PORT)) {
                                try {
                                    DnsPacket dnsPacket = udpPacket.get(DnsPacket.class);
                                    List<DnsQuestion> dnsQuestions = dnsPacket.getHeader().getQuestions();
                                    if (dnsQuestions.size() > 0) {
                                        simPacket.setDnsQname(dnsQuestions.get(0).getQName().getName());
                                    }
                                } catch (NullPointerException e) {
                                    //ignore packet that send to port 53
                                }
                            } else if (udpPacket.getHeader().getSrcPort().valueAsString().equals(Constants.DNS_PORT)) {
                                DnsPacket dnsPacket = udpPacket.get(DnsPacket.class);
                                try {

                                    List<DnsResourceRecord> dnsResourceRecords = dnsPacket.getHeader().getAnswers();
                                    List<String> answers = new ArrayList<String>();
                                    simPacket.setDnsQname(dnsPacket.getHeader().getQuestions().get(0).getQName().getName());
                                    for (DnsResourceRecord record : dnsResourceRecords) {
                                        try {
                                            DnsRDataA dnsRDataA = (DnsRDataA) record.getRData();
                                            answers.add(dnsRDataA.getAddress().getHostAddress());
                                        } catch (ClassCastException ex) {
                                            //ignore
                                        }

                                    }
                                    simPacket.setDnsAnswers(answers);
                                }catch (NullPointerException | IndexOutOfBoundsException e) {
                                    //ignore
                                }
                            }
                        } else {
                            simPacket.setSrcPort("*");
                            simPacket.setDstPort("*");
                        }
                    }
                    long startTime = System.currentTimeMillis();
                    ofSwitch.transmit(simPacket);
                    long endTime = System.currentTimeMillis();
                    sumPacketProcessingTime = sumPacketProcessingTime + (endTime-startTime);
                } catch (ClassCastException e) {
                    //ignore
                }
            }

        } catch (EOFException e) {
        } catch (NotOpenException | TimeoutException e) {
            e.printStackTrace();
        }
        System.out.println("Timetaken: " + (endTimestamp-startTimestamp) + ", Total Packets: " + totalPacketCount);
    }

}
