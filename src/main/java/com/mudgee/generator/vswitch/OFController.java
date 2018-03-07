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

import org.json.simple.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;

public class OFController {

    private static final  OFController ofController = new OFController();
    private static Map<String, OFSwitch> ofSwitchMap = new HashMap<String, OFSwitch>();
    private static Map<String, Integer> packetTransmittionMap = new HashMap<String, Integer>();
    private static List<ControllerApp> registeredApps = new ArrayList<ControllerApp>();
    private boolean packetLoggerEnabled = true;
    public static OFController getInstance() {
        return ofController;
    }

    private OFController() {

    }

    public Set<String> getSwitchIds() {
        return ofSwitchMap.keySet();
    }

    public void addSwitch(OFSwitch ofSwitch) {
        ofSwitchMap.put(ofSwitch.getDpid().toLowerCase(), ofSwitch);
        packetTransmittionMap.put(ofSwitch.getDpid().toLowerCase(), 0);
    }

    public OFSwitch getSwitch(String dpId) {
        return ofSwitchMap.get(dpId.toLowerCase());
    }

    public void registerApps(ControllerApp controllerApp, JSONObject argument) {
        registeredApps.add(controllerApp);
        controllerApp.init(argument);
    }

    public List<OFFlow> getAllFlows(String dpId) {
        return ofSwitchMap.get(dpId.toLowerCase()).getAllFlows();
    }

    public void addFlow(String dpId, OFFlow ofFlow){
        ofFlow.setLastPacketTransmittedTime(ofSwitchMap.get(dpId.toLowerCase()).getCurrentTime());
        ofSwitchMap.get(dpId.toLowerCase()).addFlow(ofFlow);
    }

    public void removeFlow(String dpId, OFFlow ofFlow) {
        ofSwitchMap.get(dpId.toLowerCase()).removeFlow(ofFlow);
    }

    public void clearAllFlows(String dpId) {
        ofSwitchMap.get(dpId.toLowerCase()).clearAllFlows();
    }

    public int  getNumperOfPackets(String dpId) {
        return packetTransmittionMap.get(dpId.toLowerCase());
    }

    public void receive(String dpId, SimPacket packet) {
        int noOFpackets = packetTransmittionMap.get(dpId.toLowerCase()) +  1;
        packetTransmittionMap.put(dpId.toLowerCase(), noOFpackets);
        //logPacket(packet);
        for (ControllerApp controllerApp : registeredApps) {
            controllerApp.process(dpId.toLowerCase(), packet);
        }

    }

    public void complete() {
        for (ControllerApp controllerApp: registeredApps) {
            controllerApp.complete();
        }
    }

    private void logPacket(SimPacket packet) {
        if (!packetLoggerEnabled) {
            return;
        }
        String currentPath = Paths.get(".").toAbsolutePath().normalize().toString();

        File workingDirectory = new File(currentPath + File.separator + "result");
        if (!workingDirectory.exists()) {
            workingDirectory.mkdir();
        }
        String filename = currentPath + File.separator + "result" + File.separator + "packetlog.txt";
        File file = new File(filename);
        FileWriter writer = null;
        try {
            writer = new FileWriter(file, true);
            writer.write(packet.getPacketInfo() + "\n");
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
