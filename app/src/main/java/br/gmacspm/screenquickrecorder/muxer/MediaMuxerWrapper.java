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

/**
 * Wrapper para o MediaMuxer, responsável por combinar (muxing) os streams de vídeo (H.264)
 * e áudio (AAC) em um único arquivo de saída MP4.
 */
public class MediaMuxerWrapper {

    private static final String TAG = "MediaMuxerWrapper";

    private final MediaMuxer mediaMuxer;
    private int videoTrackIndex = -1;
    private int audioTrackIndex = -1;
    private boolean isMuxerStarted = false;

    // Contadores para garantir que ambas as faixas foram adicionadas antes de iniciar
    private int trackCount = 0;
    private final int MAX_TRACKS = 2;

    /**
     * Construtor da classe Muxer. Cria o arquivo de saída com um nome único.
     * * @param baseDir O caminho base do diretório (Ex: context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath())
     * @throws IOException Se o Muxer não puder ser criado ou o arquivo não puder ser acessado.
     */
    public MediaMuxerWrapper(String baseDir) throws IOException {
        String fullPath = getOutputFilePath(baseDir);

        // Inicializa o MediaMuxer com o caminho completo e formato MP4
        mediaMuxer = new MediaMuxer(fullPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        Log.d(TAG, "MediaMuxerWrapper criado. Arquivo de saída: " + fullPath);
    }

    // --- Lógica de Nomenclatura de Arquivo ---

    /**
     * Gera o caminho completo e único para o arquivo de saída MP4.
     */
    private String getOutputFilePath(String baseDir) {
        // Garantir que o diretório exista
        File dir = new File(baseDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // Cria um carimbo de data/hora no formato yyyyMMdd_HHmmss
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());

        // Define o nome do arquivo final
        String fileName = "GRAVACAO_" + timeStamp + ".mp4";

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

        // Se houver dados válidos, escreve a amostra (payload)
        if (bufferInfo.size != 0) {
            // Ajusta o buffer para os dados relevantes
            encodedData.position(bufferInfo.offset);
            encodedData.limit(bufferInfo.offset + bufferInfo.size);

            // Escreve os dados (os bytes) no arquivo de saída
            mediaMuxer.writeSampleData(trackIndex, encodedData, bufferInfo);
        }
    }

    // --- Método de Finalização ---

    /**
     * Para o Muxer, finaliza o arquivo MP4 (escreve o cabeçalho 'moov') e libera os recursos.
     */
    public synchronized void release() {
        if (mediaMuxer != null) {
            try {
                if (isMuxerStarted) {
                    // Parar o Muxer é crucial para finalizar a estrutura MP4
                    mediaMuxer.stop();
                    Log.i(TAG, "MediaMuxer parado com sucesso.");
                }
            } catch (Exception e) {
                // Captura e loga o erro ao parar, mas continua para liberar
                Log.e(TAG, "Erro ao parar o MediaMuxer.", e);
            } finally {
                mediaMuxer.release();
                videoTrackIndex = -1;
                audioTrackIndex = -1;
                isMuxerStarted = false;
                Log.i(TAG, "MediaMuxer liberado.");
            }
        }
    }
}