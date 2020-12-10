package com.github.aleneum.WebbedView.gl;

import android.opengl.GLES20;
import android.util.Log;


public class Utils {
    
    private static final String LOGTAG = "Utils";

    // Enable this flag to debug OpenGL errors
    private static final boolean DEBUG_GL = false;
    
    private static int initShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (shader != 0) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(LOGTAG, "Could NOT compile shader " + shaderType + " : "
                    + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            
        }
        return shader;
    }

    public static int createProgramFromShaderSrc(String vertexShaderSrc,
        String fragmentShaderSrc) {
        int vertShader = initShader(GLES20.GL_VERTEX_SHADER, vertexShaderSrc);
        int fragShader = initShader(GLES20.GL_FRAGMENT_SHADER,
            fragmentShaderSrc);
        
        if (vertShader == 0 || fragShader == 0)
            return 0;
        
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertShader);
            checkGLError("glAttchShader(vert)");
            
            GLES20.glAttachShader(program, fragShader);
            checkGLError("glAttchShader(frag)");
            
            GLES20.glLinkProgram(program);
            int[] glStatusVar = { GLES20.GL_FALSE };
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, glStatusVar,
                0);
            if (glStatusVar[0] == GLES20.GL_FALSE)
            {
                Log.e(
                    LOGTAG,
                    "Could NOT link program : "
                        + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        
        return program;
    }

    public static void checkGLError(String op) {
        if (DEBUG_GL) {
            for (int error = GLES20.glGetError(); error != 0; error = GLES20.glGetError()) {
                Log.e(LOGTAG, "After operation " + op + " got glError 0x"
                        + Integer.toHexString(error));
            }
        }
    }
}
