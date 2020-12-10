package com.github.aleneum.WebbedView.utils;

import android.content.res.AssetManager;

import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class Parser {

    public static JSONObject createJSONFromAsset(AssetManager assets, String fileName) {
        try {
            InputStream inputStream = assets.open(fileName);
            return createJSONFromInputStream(inputStream);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return null;
    }

    public static JSONObject createJSONFromInputStream(InputStream inputStream) {

        JSONObject result = null;

        try {
            // Read file into string builder
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder builder = new StringBuilder();

            for (String line = null; (line = reader.readLine()) != null ; ) {
                builder.append(line).append("\n");
            }

            // Parse into JSONObject
            String resultStr = builder.toString();
            JSONTokener tokener = new JSONTokener(resultStr);
            result = new JSONObject(tokener);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        return result;
    }
}
