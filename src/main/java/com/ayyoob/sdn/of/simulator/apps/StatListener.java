package com.ayyoob.sdn.of.simulator.apps;

import com.ayyoob.sdn.of.simulator.SimPacket;
import org.json.simple.JSONObject;

public interface StatListener {

    void init(JSONObject jsonObject);

    void process(String dpId, SimPacket packet);

    void complete();
}
