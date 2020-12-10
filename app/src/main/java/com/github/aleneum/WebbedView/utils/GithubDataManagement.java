package com.github.aleneum.WebbedView.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;
import android.webkit.URLUtil;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.github.aleneum.WebbedView.R;

import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class GithubDataManagement implements DataManagement{
    private static GithubDataManagement instance;
    private static Context ctx;
    private static String remoteVersion;
    private static JSONObject config = null;
    private static SharedPreferences preferences;
    private static DataManagementListener managementListener;

    private RequestQueue requestQueue;

    private static final String LOGTAG = "GithubDataManagement";
    private static final String REST_API = "https://api.github.com/repos";
    private static final String CONTENT_HOST = "https://raw.githubusercontent.com/aleneum/webbed.thesis/master";
    private static final String GIT_REPO = "aleneum/webbed.thesis";
    private static final String HEAD_MASTER = "git/refs/heads/master";

    private GithubDataManagement(Context context) {
        ctx = context;
        requestQueue = getRequestQueue();
        preferences = ctx.getSharedPreferences(ctx.getString(R.string.app_config), Context.MODE_PRIVATE);
    }

    public void initialize(DataManagementListener listener) {
        managementListener = listener;
        Log.d(LOGTAG, "initialize");
        try {
            // use local version when defined in assets.
            // otherwise download data from GitHub
            InputStream configStream = ctx.getAssets().open("config.json");
            config = Parser.createJSONFromInputStream(configStream);
            listener.onDataManagementInitialized(this);
            return;
        } catch (IOException ex) {
            Log.i(LOGTAG, "No config found in assets or file not readable.");
        }

        Log.i(LOGTAG, "Triggering update request...");
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest
                (Request.Method.GET, REST_API + "/" + GIT_REPO + "/" + HEAD_MASTER, null,
                        response -> {
                            try {
                                remoteVersion = response.getJSONObject("object").getString("sha");
                                String localVersion = preferences.getString("contentVersion", "");
                                Log.d(LOGTAG,"Remote version is " + remoteVersion);
                                Log.d(LOGTAG, "Local version is " + localVersion);
                                if (!remoteVersion.equals(localVersion)) {
                                    Log.i(LOGTAG, "New content version found.");
                                    new DownloadFilesTask(DownloadFilesTask.CALLBACK_MODE.CONFIG_LOADED).execute(
                                            CONTENT_HOST + "/" + CONFIG_JSON);
                                } else {
                                    Log.i(LOGTAG, "Content is up to date");
                                    listener.onDataManagementInitialized(this);
                                }
                            } catch (JSONException ex) {
                                ex.printStackTrace();
                            }
                        }, (Response.ErrorListener) error -> {

                    Log.w(LOGTAG, "Fallback to internal storage. Could not update content. Reason: " + error.getMessage());
                    listener.onDataManagementInitialized(this);
                });
        this.addToRequestQueue(jsonObjectRequest);
    }

    public static void onConfigLoaded() {
        downloadTargets(config);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString("contentVersion", remoteVersion);
        editor.apply();
    }

    public static void onTargetsLoaded() {
        managementListener.onDataManagementInitialized(getInstance(null));
    }

    public static synchronized GithubDataManagement getInstance(Context context) {
        if (instance == null) {
            instance = new GithubDataManagement(context);
        }
        return instance;
    }

    public RequestQueue getRequestQueue() {
        if (requestQueue == null) {
            // getApplicationContext() is key, it keeps you from leaking the
            // Activity or BroadcastReceiver if someone passes one in.
            requestQueue = Volley.newRequestQueue(ctx.getApplicationContext());
        }
        return requestQueue;
    }

    public JSONObject getConfig() {
        if (config == null) {
            try {
                config = Parser.createJSONFromInputStream(ctx.openFileInput(CONFIG_JSON));
            } catch (FileNotFoundException ex) {
                Log.e(LOGTAG, "Config not found in internal storag");
                ex.printStackTrace();
            }
        }
        return config;
    }

    public <T> void addToRequestQueue(Request<T> req) {
        getRequestQueue().add(req);
    }

    private static void downloadTargets(@NotNull JSONObject config) {
        try {
            JSONArray definitions = config.getJSONArray("targetDefinitions");
            for (int i = 0; i < definitions.length(); ++i) {
                String xmlFile = definitions.getString(i);
                String datFile = xmlFile.replace(".xml", ".dat");
                new DownloadFilesTask(DownloadFilesTask.CALLBACK_MODE.TARGETS_LOADED).execute(CONTENT_HOST + "/" + xmlFile, CONTENT_HOST + "/" + datFile);
            }
        } catch (JSONException err) {
            Log.e(LOGTAG, err.getMessage());
        }
    }

    private static boolean downloadFile(String url) {
        String fileName = URLUtil.guessFileName(url, null, null);
        try {
            Log.d(LOGTAG,"Download content from " + url);
            URL u = new URL(url);
            InputStream stream = u.openConnection().getInputStream();
            FileOutputStream fos = new FileOutputStream(ctx.getFilesDir() + "/" + fileName);
            byte[] buffer = new byte[1024];
            int len = 0;
            while ( (len = stream.read(buffer)) > 0 ) {
                fos.write(buffer, 0, len);
            }
            fos.flush();
            fos.close();
            Log.d(LOGTAG, "File written to " + ctx.getFilesDir() + "/" + fileName);

        } catch(IOException e) {
            return false; // swallow a 404
        }
        return true;
    }

    private static class DownloadFilesTask extends AsyncTask<String, Void, Boolean> {

        public enum CALLBACK_MODE {
              CONFIG_LOADED,
              TARGETS_LOADED
        }
        private CALLBACK_MODE callbackMode;

        public DownloadFilesTask(CALLBACK_MODE mode) {
            callbackMode = mode;
        }

        protected Boolean doInBackground(String... urls) {
            int count = urls.length;
            for (int i = 0; i < count; i++) {
                downloadFile(urls[i]);
                // Escape early if cancel() is called
                if (isCancelled()) break;
            }
            return true;
        }

        protected void onPostExecute(Boolean result) {
            Log.i(LOGTAG, "Download finished!");
            switch (callbackMode) {
                case CONFIG_LOADED:
                    GithubDataManagement.getInstance(null).onConfigLoaded();
                    break;
                case TARGETS_LOADED:
                    GithubDataManagement.getInstance(null).onTargetsLoaded();
                    break;
            }
        }
    }
}
