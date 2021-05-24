package com.github.aleneum.WebbedView.utils;

import android.opengl.Matrix;
import android.util.Log;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Projection {

    private static final String LOGTAG = "Projection";

    private static final float[] pointTopLeft = {-0.1075f, 0.1515f};
    private static final float[] pointTopRight = {0.106f, 0.1515f};
    private static final float[] pointBottomLeft = {-0.1075f, -0.149f};
    private static final float[] pointBottomRight = {0.106f, -0.149f};

//    private static final float[] pointTopLeft = {-0.1085f, 0.1475f};
//    private static final float[] pointTopRight = {0.105f, 0.1475f};
//    private static final float[] pointBottomLeft = {-0.1085f, -0.155f};
//    private static final float[] pointBottomRight = {0.105f, -0.155f};


    private float[] vecTopLeft = {-1,1,0};
    private float[] vecTopRight = {1,1,0};
    private float[] vecBottomLeft = {-1,-1,0};
    private float[] vecBottomRight = {1,-1,0};

    private float[] projTopLeft = new float[4];
    private float[] projTopRight = new float[4];
    private float[] projBottomLeft = new float[4];
    private float[] projBottomRight = new float[4];

    private float[] mScreenMatrixAdj;
    private float mResX;
    private float mResY;

    public Projection() {
        updateResolution(100, 100);
    }

    public void updateScaling(float top, float right, float bottom, float left) {
        vecTopLeft = new float[]{left, top, 0, 1};
        vecTopRight = new float[]{right, top, 0, 1};
        vecBottomLeft= new float[]{left, bottom, 0, 1};
        vecBottomRight= new float[]{right, bottom, 0, 1};
    }

    public void updateResolution(float resX, float resY) {
        mResX = resX;
        mResY = resY;
        mScreenMatrixAdj = adjugate(
                basisToPoints(new float[]{0 , 0}, new float[]{1 , 0},
                        new float[]{0 , mResY/mResX}, new float[]{1 , mResY/mResX}));
    }

    public float[] calcProjection(@NotNull float[] matrix) {
        Matrix.multiplyMV(projTopLeft, 0, matrix, 0, vecTopLeft, 0);
        Matrix.multiplyMV(projTopRight, 0, matrix, 0, vecTopRight, 0);
        Matrix.multiplyMV(projBottomLeft, 0, matrix, 0, vecBottomLeft, 0);
        Matrix.multiplyMV(projBottomRight, 0, matrix, 0, vecBottomRight, 0);

//        List<float[]> sPoints = getScreenPoints();
        float[] p2d = general2DProjection(
                getNormalizedCoordinates(projTopLeft),
                getNormalizedCoordinates(projTopRight),
                getNormalizedCoordinates(projBottomLeft),
                getNormalizedCoordinates(projBottomRight));

        for(int i=0; i < p2d.length; ++i) { p2d[i] /= p2d[10]; }
        float[] transform = { p2d[0], p2d[4], 0,  p2d[8] / mResX,
                              p2d[1], p2d[5], 0,  p2d[9] / mResX,
                                   0,      0, 1,       0,
                              p2d[2] * mResX, p2d[6] * mResX, 0, p2d[10] };

//        List<float[]> nPoints = Arrays.asList(
//                getNormalizedCoordinates(projTopLeft), getNormalizedCoordinates(projTopRight),
//                getNormalizedCoordinates(projBottomLeft), getNormalizedCoordinates(projBottomRight));

//        String pointStringS = IntStream.range(0, sPoints.size())
//                .mapToObj(i -> Arrays.toString(sPoints.get(i)))
//                .collect(Collectors.joining(","));
//        Log.v(LOGTAG, "New Log Data");
//        Log.v(LOGTAG, pointStringS);
//        Log.v(LOGTAG, Arrays.toString(transform));
        return transform;
    }

    public List<float[]> getScreenPoints() {
        return Arrays.asList(getScreenCoordinate(projTopLeft), getScreenCoordinate(projTopRight),
                getScreenCoordinate(projBottomLeft), getScreenCoordinate(projBottomRight));
    }

    public float[] general2DProjection(float[] p1, float[] p2, float[] p3, float[] p4) {
        float[] destMatrix = basisToPoints(p1, p2, p3, p4);
        float[] res = new float[16];
        Matrix.multiplyMM(res, 0, mScreenMatrixAdj, 0, destMatrix, 0);
        return res;
    }

    private float[] basisToPoints(@NotNull float[] p1, @NotNull float[] p2,
                                  @NotNull float[] p3, @NotNull float[] p4) {
        float[] rv = new float[4];

        float[] m = { p1[0], p2[0], p3[0], 0,
                      p1[1], p2[1], p3[1], 0,
                          1,     1,     1, 0,
                          0,     0,     0, 1 };
        float[] v = { p4[0], p4[1], 1, 0};
        float[] adjM = adjugate(m);
        float[] adjMT = new float[16];
        Matrix.transposeM(adjMT, 0, adjM, 0);
        Matrix.multiplyMV(rv, 0, adjMT, 0, v, 0);

        float[] rm = { rv[0],     0,     0, 0,
                           0, rv[1],     0, 0,
                           0,     0, rv[2], 0,
                           0,     0,     0, 1 };

        Matrix.multiplyMM(rm, 0, rm, 0, m,0);

        return rm;
    }

    @Contract(pure = true)
    private float[] adjugate(@NotNull float[] m) {
        float[] r = {
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0,
            0, 0, 0, 0 };
        r[0] = m[5]*m[10]-m[6]*m[9];  r[1] = m[2]*m[9]-m[1]*m[10]; r[2] = m[1]*m[6]-m[2]*m[5];
        r[4] = m[6]*m[8]-m[4]*m[10];  r[5] = m[0]*m[10]-m[2]*m[8]; r[6] = m[2]*m[4]-m[0]*m[6];
        r[8] = m[4]*m[9]-m[5]*m[8];   r[9] = m[1]*m[8]-m[0]*m[9]; r[10] = m[0]*m[5]-m[1]*m[4];
        r[15] = 1;
        return r;
    }

    @Contract(value = "_ -> new", pure = true)
    @NotNull
    private float[] getNormalizedCoordinates(@org.jetbrains.annotations.NotNull float[] point) {
        return new float[]{(1 + point[0]/point[3]) / 2,
                           (1 - point[1]/point[3]) / 2 * mResY/mResX};
    }

    private float[] getScreenCoordinate(@org.jetbrains.annotations.NotNull float[] point) {
        return new float[]{(1 + point[0]/point[3]) / 2 * mResX,
                           (1 - point[1]/point[3]) / 2 * mResY};
    }
}
