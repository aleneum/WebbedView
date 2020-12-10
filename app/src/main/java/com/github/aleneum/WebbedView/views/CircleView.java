package com.github.aleneum.WebbedView.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

public class CircleView extends View
{

    public List<float[]> mPoints = null;
    Paint paint = null;
    private static final String LOGTAG = "Circles";

    public CircleView(Context context, AttributeSet attrs)
    {
        super(context, attrs);
        this.setWillNotDraw(false);
        paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.BLACK);
    }
    
    @Override
    protected void onDraw(Canvas canvas)
    {
        if (mPoints != null) {
            super.onDraw(canvas);
            int radius = 20;
            for (float[] p : mPoints) {
                canvas.drawCircle(p[0]*2, p[1]*2, radius, paint);
            }
        }

    }
}