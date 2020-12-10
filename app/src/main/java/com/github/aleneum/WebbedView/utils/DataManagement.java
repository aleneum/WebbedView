package com.github.aleneum.WebbedView.utils;

import org.json.JSONObject;

public interface DataManagement {
    String CONFIG_JSON = "config.json";
    JSONObject getConfig();
    void initialize(DataManagementListener listener);
}
