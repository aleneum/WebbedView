package com.github.aleneum.WebbedView.utils;

import android.os.CountDownTimer;

public class Timer extends CountDownTimer {
    private boolean mIsRunning = false;

    protected Timer(long timerLength, long timerInterval) {
        super(timerLength, timerInterval);
    }

    @Override
    public void onTick(long l) {
        if (!mIsRunning)
        {
            mIsRunning = true;
        }
    }

    @Override
    public void onFinish() {
        mIsRunning = false;
    }


    public boolean isRunning() {
        return mIsRunning;
    }


    public void stopTimer() {
        cancel();
        mIsRunning = false;
    }


    public void startTimer() {
        mIsRunning = true;
        start();
    }
}
