package br.gmacspm.screenquickrecorder.recorder;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;

import java.io.IOException;
import java.nio.ByteBuffer;

import br.gmacspm.screenquickrecorder.muxer.MediaMuxerWrapper;

public class InternalAudioRecorder {
    private AudioRecord audioRecord;
    private boolean isCapturing = false;
    private Thread captureThread;
    private final MediaMuxerWrapper muxer;
    private MediaCodec encoder;
    private int audioTrackIndex = -1;

    @SuppressLint("MissingPermission")
    public InternalAudioRecorder(Context context, MediaProjection mediaProjection) {
        this.context = context;
    public InternalAudioRecorder(MediaProjection mediaProjection, MediaMuxerWrapper muxer) {
        this.muxer = muxer;

        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

        AudioFormat audioFormat = new AudioFormat.Builder()
                .setSampleRate(44100)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_IN_STEREO)
                .build();

        int bufferSize = AudioRecord.getMinBufferSize(
                44100,
                AudioFormat.CHANNEL_IN_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();

    }

    public void startInternalAudioCapture() {
        audioRecord.startRecording();
        isCapturing = true;
        captureThread = new Thread(() -> {
            int sampleRate = 44100;
            int channelCount = 2;

            MediaFormat format = MediaFormat.createAudioFormat(
                    MediaFormat.MIMETYPE_AUDIO_AAC,
                    sampleRate,
                    channelCount
            );
            format.setInteger(MediaFormat.KEY_AAC_PROFILE,
                    MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            try {
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            byte[] buffer = new byte[4096];

            while (isCapturing) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    drainEncoder(encoder, buffer);
                }
            }

            encoder.stop();
            encoder.release();
            audioRecord.stop();

        });
        captureThread.start();
    }

    void drainEncoder(MediaCodec audioCodec, byte[] pcm) {
        int inIndex = audioCodec.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            ByteBuffer buffer = audioCodec.getInputBuffer(inIndex);
            buffer.clear();
            buffer.put(pcm);
            audioCodec.queueInputBuffer(inIndex, 0, pcm.length, System.nanoTime() / 1000, 0);
        }

        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex;

        while ((outIndex = audioCodec.dequeueOutputBuffer(info, 10000)) >= 0) {
            if (audioTrackIndex == -1) {
                audioTrackIndex = muxer.addTrack(audioCodec.getOutputFormat());
            }

            ByteBuffer encodedData = audioCodec.getOutputBuffer(outIndex);
            if (encodedData != null) {
                muxer.writeSampleData(audioTrackIndex, encodedData, info);
            }

            audioCodec.releaseOutputBuffer(outIndex, false);
        }
    }

    public void stopInternalAudioCapture() {
        isCapturing = false;

        if (captureThread != null) {
            try {
                captureThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            captureThread = null;
        }

        if (audioRecord != null) {
            try {
                audioRecord.stop();
            } catch (Exception ignored) {
            }
            audioRecord.release();
            audioRecord = null;
        }
    }
}
