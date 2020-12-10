package com.github.aleneum.WebbedView.ui.ActivityList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;

import com.github.aleneum.WebbedView.R;


public class ActivitySplashScreen extends Activity
{

    long SPLASH_DURATION_MILLIS = 450;

    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN);

        RelativeLayout layout = (RelativeLayout) View.inflate(this, R.layout.splash_screen, null);
        
        addContentView(layout, new LayoutParams(LayoutParams.MATCH_PARENT,
            LayoutParams.MATCH_PARENT));


        final Handler handler = new Handler();
        handler.postDelayed(this::runAR, this.SPLASH_DURATION_MILLIS);
    }

    public void runAR() {
        String mClassToLaunchPackage = getPackageName();
        String mClassToLaunch = mClassToLaunchPackage + ".tracking.ImageTargets";
        Intent i = new Intent();
        i.setClassName(mClassToLaunchPackage, mClassToLaunch);
        startActivity(i);
    }
}
