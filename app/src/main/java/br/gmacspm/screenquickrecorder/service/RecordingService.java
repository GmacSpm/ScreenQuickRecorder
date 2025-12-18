package br.gmacspm.screenquickrecorder.service;

// RecordingService.java

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.IOException;

import br.gmacspm.screenquickrecorder.muxer.MediaMuxerWrapper;
import br.gmacspm.screenquickrecorder.recorder.InternalAudioRecorder;
import br.gmacspm.screenquickrecorder.recorder.ScreenRecorder;

public class RecordingService extends Service {
    private static final String TAG = "RecordingService";
    public static final String EXTRA_RESULT_CODE = "extra_result_code";
    public static final String EXTRA_RESULT_INTENT = "extra_result_intent";
    private static final int NOTIF_ID = 1;
    private static final String CHANNEL_ID = "screen_rec_channel";

    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;

    private int width;
    private int height;
    private int density;

    private static volatile boolean recording = false;
    private ScreenRecorder screenRecorder;
    private InternalAudioRecorder audioRecorder;
    private MediaMuxerWrapper muxerWrapper;
    private BroadcastReceiver screenReceiver;

    public static boolean isRecording() {
        return recording;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        screenReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                    onScreenOff();
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        registerReceiver(screenReceiver, filter);

        projectionManager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        wm.getDefaultDisplay().getRealMetrics(metrics);

        density = metrics.densityDpi;
        width = metrics.widthPixels;
        height = metrics.heightPixels;

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Gravando tela..."));
    }

    private void onScreenOff() {
        stopRecording();
    }

    private void startRecording() {
        if (mediaProjection == null) return;

        try {
            muxerWrapper = new MediaMuxerWrapper(getExternalMediaPath());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        screenRecorder = new ScreenRecorder(muxerWrapper, width, height);
        audioRecorder = new InternalAudioRecorder(mediaProjection, muxerWrapper);

        screenRecorder.start();
        audioRecorder.startInternalAudioCapture();

        // Crie o VirtualDisplay para capturar a tela
        virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                screenRecorder.getInputSurface(),
                null, null
        );

        recording = true;
        Toast.makeText(this, "GRAVANDO", Toast.LENGTH_LONG).show();
    }

    private void stopRecording() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
        }
        if (screenRecorder != null) {
            screenRecorder.stop();
            screenRecorder = null;
        }
        if (audioRecorder != null) {
            audioRecorder.stopInternalAudioCapture();
            audioRecorder = null;
        }
        if (mediaProjection != null) {
            mediaProjection.stop();
        }
        if (muxerWrapper != null) {
            muxerWrapper.release();
        }

        recording = false;
        stopForeground(true);
        stopSelf();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        if ("com.example.app.ACTION_STOP_RECORDING".equals(action)) {
            stopRecording();
            return START_NOT_STICKY;
        }

        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED);
        if (resultCode == Activity.RESULT_OK) {
            Intent data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
            if (data != null) {
                mediaProjection = projectionManager.getMediaProjection(resultCode, data);
                if (mediaProjection != null) {
                    startRecording();
                }
            }
        }

        return START_NOT_STICKY;
    }


    private String getExternalMediaPath() {
        File[] mediaDirs = getExternalMediaDirs();
        File mediaDir = (mediaDirs != null && mediaDirs.length > 0) ? mediaDirs[0] : null;

        if (mediaDir != null) {
            if (!mediaDir.exists()) {
                mediaDir.mkdirs();
            }
            return mediaDir.getAbsolutePath();
        } else {
            return getFilesDir().getAbsolutePath();
        }
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(screenReceiver);
        recording = false;
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Utilitários de notif
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Gravação de tela", NotificationManager.IMPORTANCE_LOW);
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.createNotificationChannel(ch);
    }

    private android.app.Notification buildNotification(String text) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Gravador de tela")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setOngoing(true);
        return builder.build();
    }
}

