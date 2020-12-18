package com.github.aleneum.WebbedView.views;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.github.aleneum.WebbedView.Constants;
import com.github.aleneum.WebbedView.R;
import com.github.aleneum.WebbedView.utils.Projection;

public class CustomWebView extends WebView {

    private final String LOGTAG = "CustomWebView";
    public Activity mActivity = null;
    private boolean mLoaded = false;
    private String mTransformedElement;
    private Projection mProjection;
    public boolean shouldIntent = true;
    private boolean mShouldIntent = shouldIntent;

    public CustomWebView(Context context) {
        this(context, null);
    }

    public CustomWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setVisibility(INVISIBLE);
        this.setBackgroundColor(Color.TRANSPARENT);
        this.getSettings().setJavaScriptEnabled(true);
        this.getSettings().setDomStorageEnabled(true);
        this.getSettings().setDatabaseEnabled(true);
        this.getSettings().setMediaPlaybackRequiresUserGesture(false);
        this.setWebContentsDebuggingEnabled(true);
        this.clearCache(true);
        mProjection = new Projection();

        this.setWebViewClient(new WebViewClient(){
            @Override
            public void onPageFinished(WebView view, String url) {
                Log.d(LOGTAG, "URL: " + url + " loaded");
                String style = mTransformedElement + ".style.transformOrigin=\"0 0 0\";"
                             + mTransformedElement + ".style.backgroundColor=\"transparent\"";
                view.post(() -> {
                    setVisibility(VISIBLE);
                    view.evaluateJavascript(style, null);
                    view.evaluateJavascript("(function(){return window.innerWidth + ',' + window.innerHeight})()", new ValueCallback<String>() {
                        @Override
                        public void onReceiveValue(String s) {
                            initProjection(s);
                        }
                    });
                });
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (mShouldIntent & shouldIntent) {
                    Log.i(LOGTAG, "Convert request into intent!");
                    Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    view.getContext().startActivity(intent);
                    return true;
                }
                return false;
            }
        });
    }

    public void initProjection(String result) {
        // String should be "<width>px,<height>px"
        try {
            Log.d(LOGTAG, "LOADED: " + result);
            String[] splits = result.split(",");
            String width = splits[0].replaceAll("[^\\d.]", "");
            String height = splits[1].replaceAll("[^\\d.]", "");
            // remove 'px' from string
            float resX = Float.parseFloat(width);
            float resY = Float.parseFloat(height);

            // Browser and Screen resolution do not match
            // add aspect ratio to fix CircleView
            DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
            float mDisplayX = displayMetrics.widthPixels;
            float mDisplayY = displayMetrics.heightPixels;
            CircleView view = mActivity.findViewById(R.id.circles);

            view.mAspectX = mDisplayX / resX;
            view.mAspectY = mDisplayY / resY;

            Log.d(LOGTAG, Float.toString(resX) + ":" + Float.toString(resY));
            mProjection.updateResolution(resX, resY);
            mLoaded = true;
            mShouldIntent = shouldIntent;

        } catch (NumberFormatException e) {
            Log.w(LOGTAG, e.toString());
        }
    }

    public void getRemoteContent(String url) {
        this.getRemoteContent(url, null);
    }

    public void getRemoteContent(String url, String elementId) {
        final String resolvedUrl = (url.startsWith("http")) ? url : Constants.CONTENT_HOST + "/" + url;
        mLoaded = false;
        mShouldIntent = false;
        mTransformedElement = (elementId != null) ? "document.getElementById('" + elementId + "')" : "document.body";
        this.post(() -> {
            setVisibility(INVISIBLE);
            Log.d(LOGTAG, "Loading: " + resolvedUrl);
            super.loadUrl(resolvedUrl);
        });
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        Log.d(LOGTAG, "Got touch event");
        if (mActivity != null) {
            mActivity.onTouchEvent(event);
        }
        return super.onTouchEvent(event);
    }

    public void updateWebViewTransform(float[] matrix) {
        float[] transform = mProjection.calcProjection(matrix);
        this.debugWebViewMatrix(mProjection.getScreenPoints());

        String matString = IntStream.range(0, transform.length)
                .mapToObj(i -> Float.toString(transform[i]))
                .collect(Collectors.joining(","));
        String call = mTransformedElement + ".style.transform=\"matrix3d(" + matString + ")\";";
        Log.v(LOGTAG, "update transform with JS call: " + call);
        this.post(() -> {
            super.evaluateJavascript(call, null);
        });
    }

    public boolean isLoaded() {
        return mLoaded;
    }

    public void debugWebViewMatrix(List<float[]> points) {
        CircleView view = mActivity.findViewById(R.id.circles);
        view.mPoints = points;

//        String pointString = IntStream.range(0, points.size())
//                .mapToObj(i -> Arrays.toString(points.get(i)))
//                .collect(Collectors.joining(","));
        // showPoints only works when debug.html in assets/html is served and targeted
        view.post(() -> {
            view.invalidate();
//            this.evaluateJavascript("showPoints([" + pointString + "])", null);
        });
    }

    public void setProjectionScaling(float[] params) {
        Log.d(LOGTAG, "Update projection scaling");
        mProjection.updateScaling(params[0], params[1], params[2], params[3]);
    }

}