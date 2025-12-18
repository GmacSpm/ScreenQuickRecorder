package br.gmacspm.screenquickrecorder.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import android.util.Log;

public class MediaMuxerWrapper {

    private static final String TAG = "MediaMuxerWrapper";

    private final MediaMuxer mediaMuxer;
    private boolean isMuxerStarted = false;
    private int trackCount = 0;
    private final int EXPECTED_TRACKS = 2; // Vídeo e Áudio

    public MediaMuxerWrapper(String baseDir) throws IOException {
        String fullPath = getOutputFilePath(baseDir);
        mediaMuxer = new MediaMuxer(fullPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Log.d(TAG, "MediaMuxerWrapper criado. Arquivo de saída: " + fullPath);
    }

    private String getOutputFilePath(String baseDir) {
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String timeStamp = new SimpleDateFormat("dd-MM-yyyy_HH-mm-ss", Locale.US).format(new Date());
        String fileName = "recorded_" + timeStamp + ".mp4";
        return baseDir + File.separator + fileName;
    }

    public synchronized int addTrack(MediaFormat format) {
        if (isMuxerStarted) {
            return -1;
        }
        int trackIndex = mediaMuxer.addTrack(format);
        trackCount++;
        Log.i(TAG, "Trilha adicionada com índice: " + trackIndex);
        if (trackCount == EXPECTED_TRACKS) {
            mediaMuxer.start();
            isMuxerStarted = true;
            Log.i(TAG, "MediaMuxer **INICIADO**.");
        }
        return trackIndex;
    }

    public synchronized void writeSampleData(int trackIndex, ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        if (!isMuxerStarted) {
            return;
        }
        if (trackIndex < 0) {
            return;
        }
        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
            bufferInfo.size = 0;
        }
        if (bufferInfo.size != 0) {
            encodedData.position(bufferInfo.offset);
            encodedData.limit(bufferInfo.offset + bufferInfo.size);
            mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
        }
    }

    public synchronized void release() {
        if (mediaMuxer != null) {
            try {
                if (isMuxerStarted) {
                    mediaMuxer.stop();
                    Log.i(TAG, "MediaMuxer parado com sucesso.");
                }
            } catch (Exception e) {
                Log.e(TAG, "Erro ao parar o MediaMuxer.", e);
            } finally {
                mediaMuxer.release();
                isMuxerStarted = false;
                Log.i(TAG, "MediaMuxer liberado.");
            }
        }
    }
}