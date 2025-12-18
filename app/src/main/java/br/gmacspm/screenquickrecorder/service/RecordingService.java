package br.gmacspm.screenquickrecorder.service;

// RecordingService.java

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.hardware.display.VirtualDisplay;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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

    private MediaCodec videoEncoder;
    private Surface inputSurface;

    private MediaCodec audioEncoder;
    private AudioRecord audioRecord;

    private MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean muxerStarted = false;

    private boolean isStopping = false;
    private HandlerThread encoderThread;
    private Handler encoderHandler;

    private int width;
    private int height;
    private int density;

    private static volatile boolean recording = false;
    private ScreenRecorder screenRecorder;
    private InternalAudioRecorder audioRecorder;
    private MediaMuxerWrapper muxerWrapper;

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

        DisplayMetrics metrics = getResources().getDisplayMetrics();
        density = metrics.densityDpi;
        width = metrics.widthPixels;
        height = metrics.heightPixels;

        encoderThread = new HandlerThread("EncoderThread");
        encoderThread.start();
        encoderHandler = new Handler(encoderThread.getLooper());

        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification("Gravando tela..."));
    }

    private void onScreenOff() {
        stopRecording();
    }

    private void startRecording() {
        if (mediaProjection == null) return;

        muxerWrapper = new MediaMuxerWrapper(generateFilePath());

        screenRecorder = new ScreenRecorder(this, mediaProjection, muxerWrapper);
        audioRecorder = new InternalAudioRecorder(this, mediaProjection, muxerWrapper);
        screenRecorder.start(generateFilePath());
        audioRecorder.startInternalAudioCapture();
        recording = true;
    }

    private void stopRecording() {
        if (screenRecorder != null) {
            screenRecorder.stop();
            screenRecorder = null;
            audioRecorder.stopInternalAudioCapture();
            audioRecorder = null;
            recording = false;
        }

        stopForeground(true);
        stopSelf();
    }


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        int resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_OK);
        Intent data = intent.getParcelableExtra(EXTRA_RESULT_INTENT);
        if (resultCode == Activity.RESULT_OK) {

            MediaProjectionManager pm =
                    (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);

            mediaProjection = pm.getMediaProjection(resultCode, data);

            startRecording();
        } else if ("com.example.app.ACTION_STOP_RECORDING".equals(action)) {
            stopRecording();
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
        recording = false;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Utilitários de notif
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Gravação de tela", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
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

