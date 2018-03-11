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

import java.util.List;

public class SimPacket {

    private long size;
    private String dhcpHostname;
    private String srcMac;
    private String dstMac;
    private String ethType;
    private String srcIp;
    private String dstIp;
    private String ipProto;
    private String srcPort;
    private String dstPort;
    private byte[] header;
    private byte[] data;
    private String dnsQname;
    private Flag tcpFlag;
    private String icmpType;
    private String icmpCode;


    public enum Flag {
        SYN,
        SYN_ACK,
        OTHER
    }

    public String getdnsQname() {
        return dnsQname;
    }

    public void setDnsQname(String dnsQname) {
        this.dnsQname = dnsQname;
    }

    public List<String> getDnsAnswers() {
        return dnsAnswers;
    }

    public void setDnsAnswers(List<String> dnsAnswers) {
        this.dnsAnswers = dnsAnswers;
    }

    private List<String> dnsAnswers;
    private long timestamp;

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getSrcMac() {
        return srcMac;
    }

    public void setSrcMac(String srcMac) {
        this.srcMac = srcMac;
    }

    public String getDstMac() {
        return dstMac;
    }

    public void setDstMac(String dstMac) {
        this.dstMac = dstMac;
    }

    public String getEthType() {
        return ethType;
    }

    public void setEthType(String ethType) {
        this.ethType = ethType;
    }

    public String getSrcIp() {
        return srcIp;
    }

    public void setSrcIp(String srcIp) {
        this.srcIp = srcIp;
    }

    public String getDstIp() {
        return dstIp;
    }

    public void setDstIp(String dstIp) {
        this.dstIp = dstIp;
    }

    public void setIpProto(String ipProto) {
        this.ipProto = ipProto;
    }

    public String getIpProto() {
        return ipProto;
    }

    public String getSrcPort() {
        return srcPort;
    }

    public void setSrcPort(String srcPort) {
        this.srcPort = srcPort;
    }

    public String getDstPort() {
        return dstPort;
    }

    public void setDstPort(String dstPort) {
        this.dstPort = dstPort;
    }

    public byte[] getHeader() {
        return header;
    }

    public void setHeader(byte[] header) {
        this.header = header;
    }

    public byte[] getData() {
        return data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public Flag getTcpFlag() {
        return tcpFlag;
    }

    public void setTcpFlag(boolean syn, boolean ack) {
        tcpFlag = Flag.OTHER;
        if (syn) {
            tcpFlag = Flag.SYN;
            if (ack) {
                tcpFlag = Flag.SYN_ACK;
            }
        }
    }

    public void print() {
        System.out.println("size, srcMac, dstMac, ethType, srcIp, dstIp, ipProto, srcPort, dstPort,icmpCode,icmpType,timestamp");
        System.out.println(size + "," + srcMac + "," + dstMac + "," + ethType + "," + srcIp + "," + dstIp
                + "," + ipProto + "," + srcPort + "," + dstPort + "," + icmpCode + "," + icmpType + "," + timestamp);
    }

    public String getPacketInfo() {
        return size + "," + srcMac + "," + dstMac + "," + ethType + "," + srcIp + "," + dstIp
                + "," + ipProto + "," + srcPort + "," + dstPort + "," + icmpCode + "," + icmpType + "," + timestamp;
    }

    public String getIcmpType() {
        return icmpType;
    }

    public void setIcmpType(String icmpType) {
        this.icmpType = icmpType;
    }

    public String getIcmpCode() {
        return icmpCode;
    }

    public void setIcmpCode(String icmpCode) {
        this.icmpCode = icmpCode;
    }
}
