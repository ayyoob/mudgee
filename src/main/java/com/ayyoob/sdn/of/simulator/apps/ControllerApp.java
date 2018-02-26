package com.ayyoob.sdn.of.simulator.apps;

import com.ayyoob.sdn.of.simulator.SimPacket;
import org.json.simple.JSONObject;

public interface ControllerApp {

    void init(JSONObject jsonObject);

    void process(String dpId, SimPacket packet);

    void complete();
}
