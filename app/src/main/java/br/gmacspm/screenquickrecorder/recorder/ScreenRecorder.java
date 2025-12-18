package br.gmacspm.screenquickrecorder.recorder;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.MenuInflater;
import android.view.Surface;

import br.gmacspm.screenquickrecorder.muxer.MediaMuxerWrapper;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

public class ScreenRecorder implements Runnable {

    private static final String TAG = "ScreenRecorder";
    private final MediaMuxerWrapper mMuxer;
    private final AtomicBoolean isRecording = new AtomicBoolean(false);

    private int mWidth;
    private int mHeight;
    private final int mBitRate = 8000000;
    private final int mFrameRate = 60;

    private MediaCodec mVideoEncoder;
    private Surface mInputSurface;
    private int videoTrackIndex = -1;

    public ScreenRecorder(MediaMuxerWrapper muxer, int width, int height) {
        this.mMuxer = muxer;
        this.mWidth = width;
        this.mHeight = height;
    }

    public void prepareEncoder() throws IOException {
        String videoMime = MediaFormat.MIMETYPE_VIDEO_AVC;

        MediaFormat format = MediaFormat.createVideoFormat(videoMime, mWidth, mHeight);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mBitRate);
        format.setInteger(MediaFormat.KEY_OPERATING_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, mFrameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        format.setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR);

        if (videoMime.equals(MediaFormat.MIMETYPE_VIDEO_AVC)) {
            format.setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileHigh);
            format.setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel41);
        }

        mVideoEncoder = MediaCodec.createEncoderByType(videoMime);
        mVideoEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mInputSurface = mVideoEncoder.createInputSurface();
        mVideoEncoder.start();
        Log.i(TAG, "Encoder de Vídeo configurado e iniciado.");
    }

    public Surface getInputSurface() {
        return mInputSurface;
    }

    public void start() {
        try {
            prepareEncoder();
            isRecording.set(true);
            new Thread(this, "VideoEncoderThread").start();
        } catch (IOException e) {
            Log.e(TAG, "Falha ao iniciar o encoder de vídeo", e);
            release();
        }
    }

    public void stop() {
        isRecording.set(false);
        if (mVideoEncoder != null) {
            mVideoEncoder.signalEndOfInputStream();
        }
    }

    @Override
    public void run() {
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        final long TIMEOUT_USEC = 10000;

        Log.i(TAG, "Loop de codificação de vídeo iniciado.");

        while (isRecording.get() || (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) == 0) {
            int outIndex = mVideoEncoder.dequeueOutputBuffer(info, TIMEOUT_USEC);

            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!isRecording.get()) {
                    Log.d(TAG, "Nenhum dado, mas gravação parando.");
                }
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                MediaFormat newFormat = mVideoEncoder.getOutputFormat();
                Log.i(TAG, "Formato de vídeo pronto: " + newFormat);
                videoTrackIndex = mMuxer.addTrack(newFormat);
            } else if (outIndex >= 0) {
                ByteBuffer encodedData = mVideoEncoder.getOutputBuffer(outIndex);
                if (encodedData != null && info.size > 0) {
                    mMuxer.writeSampleData(videoTrackIndex, encodedData, info);
                }
                mVideoEncoder.releaseOutputBuffer(outIndex, false);
            }

            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                Log.i(TAG, "Fim da stream de vídeo alcançado.");
                break;
            }
        }

        Log.i(TAG, "Loop de codificação de vídeo encerrado.");
        release();
    }

    private void release() {
        if (mVideoEncoder != null) {
            try {
                mVideoEncoder.stop();
                mVideoEncoder.release();
            } catch (Exception e) {
                Log.e(TAG, "Erro ao liberar o encoder de vídeo.", e);
            }
            mVideoEncoder = null;
        }
        if (mInputSurface != null) {
            mInputSurface.release();
            mInputSurface = null;
        }
    }
}