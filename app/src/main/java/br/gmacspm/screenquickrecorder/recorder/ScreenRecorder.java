package br.gmacspm.screenquickrecorder.recorder;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.AudioAttributes;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.MediaRecorder;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

import java.io.File;

public class ScreenRecorder {

    private static final String TAG = "ScreenRecorder";

    private final Context context;
    private final MediaProjection projection;

    private MediaRecorder mMediaRecorder;
    private VirtualDisplay mVirtualDisplay;

    private Surface mSurface;

    private int mWidth;
    private int mHeight;
    private int mDensity;

    private boolean isRecording = false;

    public ScreenRecorder(Context ctx, MediaProjection mp) {
        this.context = ctx;
        this.projection = mp;

        loadScreenInfo();
    }

    /** Obtém tamanho da tela (px) e densidade */
    private void loadScreenInfo() {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(dm);

        mWidth = dm.widthPixels;
        mHeight = dm.heightPixels;
        mDensity = dm.densityDpi;
    }

    // ---------------------------------------------------------------------
    //  PUBLIC API
    // ---------------------------------------------------------------------
    public void start(String path) {
        if (isRecording) return;

        initMediaRecorder(path);
        startVirtualDisplay();

        mMediaRecorder.start();
        isRecording = true;

        Log.d(TAG, "Screen recording STARTED");
    }

    public void stop() {
        if (!isRecording) return;

        try {
            mMediaRecorder.stop();
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parar MediaRecorder: " + e);
        }

        releaseResources();
        isRecording = false;

        Log.d(TAG, "Screen recording STOPPED");
    }

    // ---------------------------------------------------------------------
    //  MediaRecorder SETUP
    // ---------------------------------------------------------------------
    private void initMediaRecorder(String path) {

        final int BIT_RATE = 5 * 1024 * 1024; // 5Mbps
        final int FRAME_RATE = 30;

        if (mMediaRecorder == null) {
            mMediaRecorder = new MediaRecorder();
        } else {
            mMediaRecorder.reset();
        }

        try {
            // --------------------------
            //  VIDEO SOURCE
            // --------------------------
            mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
            // --------------------------
            //  OUTPUT FORMAT
            // --------------------------
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

            // --------------------------
            //  VIDEO SETTINGS
            // --------------------------
            mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
            mMediaRecorder.setVideoSize(mWidth, mHeight);
            mMediaRecorder.setVideoFrameRate(FRAME_RATE);
            mMediaRecorder.setVideoEncodingBitRate(BIT_RATE);
            // --------------------------
            //  OUTPUT FILE
            // --------------------------
            mMediaRecorder.setOutputFile(path);

            // PREPARE & get SURFACE
            mMediaRecorder.prepare();
            mSurface = mMediaRecorder.getSurface();

        } catch (Exception e) {
            Log.e(TAG, "Erro initMediaRecorder(): " + e);
        }
    }

    // ---------------------------------------------------------------------
    //  VirtualDisplay
    // ---------------------------------------------------------------------
    private void startVirtualDisplay() {
        mVirtualDisplay = projection.createVirtualDisplay(
                "ScreenRecorderDisplay",
                mWidth,
                mHeight,
                mDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mSurface,
                null,
                null
        );
    }

    // ---------------------------------------------------------------------
    //  RELEASE
    // ---------------------------------------------------------------------
    private void releaseResources() {
        try {
            if (mVirtualDisplay != null) {
                mVirtualDisplay.release();
                mVirtualDisplay = null;
            }
        } catch (Exception ignored) {}

        try {
            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        } catch (Exception ignored) {}

        try {
            projection.stop();
        } catch (Exception ignored) {}
    }

    // ---------------------------------------------------------------------
    //  FILE OUTPUT
    // ---------------------------------------------------------------------
}
