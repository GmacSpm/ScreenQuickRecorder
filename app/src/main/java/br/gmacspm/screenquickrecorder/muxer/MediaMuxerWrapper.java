package br.gmacspm.screenquickrecorder.muxer;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MediaMuxerWrapper {

    private static final String TAG = "MediaMuxerWrapper";

    private final MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean isMuxerStarted = false;
    private int trackCount = 0;
    private final int MAX_TRACKS = 2;

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

    // --- Métodos de Adição de Faixa ---

    /**
     * Adiciona a faixa de vídeo. Deve ser chamada pela thread do Encoder de Vídeo.
     */
    public synchronized void addVideoTrack(MediaFormat format) {
        if (isMuxerStarted) return;

        videoTrackIndex = mediaMuxer.addTrack(format);
        trackCount++;
        Log.i(TAG, "Faixa de vídeo adicionada com índice: " + videoTrackIndex);
        attemptMuxerStart();
    }

    /**
     * Adiciona a faixa de áudio. Deve ser chamada pela thread do Encoder de Áudio.
     */
    public synchronized void addAudioTrack(MediaFormat format) {
        if (isMuxerStarted) return;

        audioTrackIndex = mediaMuxer.addTrack(format);
        trackCount++;
        Log.i(TAG, "Faixa de áudio adicionada com índice: " + audioTrackIndex);
        attemptMuxerStart();
    }

    /**
     * Inicia o MediaMuxer se ambas as faixas (Vídeo e Áudio) já tiverem sido adicionadas.
     */
    private synchronized void attemptMuxerStart() {
        if (trackCount == MAX_TRACKS && !isMuxerStarted) {
            mediaMuxer.start();
            isMuxerStarted = true;
            Log.i(TAG, "MediaMuxer **INICIADO** (Vídeo e Áudio prontos para escrita).");
        }
    }

    // --- Método Principal de Escrita (Recebe os Bytes) ---

    /**
     * Escreve os dados codificados (bytes de Vídeo ou Áudio) no MediaMuxer.
     * Este é o método onde os bytes codificados do MediaCodec são passados para o Muxer.
     * * @param isVideo Indica se o buffer é de Vídeo (true) ou Áudio (false).
     * @param encodedData O ByteBuffer contendo os dados codificados.
     * @param bufferInfo Metadados do buffer, incluindo presentationTimeUs.
     */
    public synchronized void writeSampleData(boolean isVideo, ByteBuffer encodedData, MediaCodec.BufferInfo bufferInfo) {
        if (!isMuxerStarted) {
            return;
        }

        int trackIndex = isVideo ? videoTrackIndex : audioTrackIndex;

        if (trackIndex < 0) return;

        // Tratar dados de configuração
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