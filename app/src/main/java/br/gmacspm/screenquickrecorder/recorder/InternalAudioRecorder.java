package br.gmacspm.screenquickrecorder.recorder;

import android.annotation.SuppressLint;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioPlaybackCaptureConfiguration;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.projection.MediaProjection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

public class InternalAudioRecorder {

    private final Context context;
    private AudioRecord audioRecord;
    private boolean isCapturing = false;
    private Thread captureThread;

    @SuppressLint("MissingPermission")
    public InternalAudioRecorder(Context context, MediaProjection mediaProjection) {
        this.context = context;

        // 1) Configuração para capturar áudio interno
        AudioPlaybackCaptureConfiguration config =
                new AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                        .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                        .addMatchingUsage(AudioAttributes.USAGE_GAME)
                        .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                        .build();

        // 2) Formato do áudio PCM
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

        // 3) Instancia o AudioRecord com captura interna
        audioRecord = new AudioRecord.Builder()
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setAudioPlaybackCaptureConfig(config)
                .build();

    }

    public void startInternalAudioCapture() {
        audioRecord.startRecording();
        isCapturing = true;
        // 4) Thread que lê o áudio PCM constantemente
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
            format.setInteger(MediaFormat.KEY_BIT_RATE, 128000);  // 128 kbps
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);

            MediaCodec encoder = null;
            try {
                encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();


            File file = new File(context.getExternalFilesDir(null), "audio_interno.wav");
            FileOutputStream fos = null;
            try {
                fos = new FileOutputStream(file);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }

            audioRecord.startRecording();
            byte[] buffer = new byte[4096];

            while (isCapturing) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    byte[] data = Arrays.copyOf(buffer, read);
                    try {
                        encodePCMtoAAC(encoder, data, fos);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            }

            try {
                fos.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            encoder.stop();
            encoder.release();
            audioRecord.stop();

        });
        captureThread.start();
    }

    void encodePCMtoAAC(MediaCodec audioCodec, byte[] pcm, FileOutputStream out) throws IOException {
        ByteBuffer[] inputBuffers = audioCodec.getInputBuffers();
        ByteBuffer[] outputBuffers = audioCodec.getOutputBuffers();

        // ---- INPUT (PCM) ----
        int inIndex = audioCodec.dequeueInputBuffer(10000);
        if (inIndex >= 0) {
            ByteBuffer buffer = inputBuffers[inIndex];
            buffer.clear();
            buffer.put(pcm);
            audioCodec.queueInputBuffer(inIndex, 0, pcm.length, System.nanoTime() / 1000, 0);
        }

        // ---- OUTPUT (AAC) ----
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        int outIndex;

        while ((outIndex = audioCodec.dequeueOutputBuffer(info, 10000)) >= 0) {
            ByteBuffer encodedData = outputBuffers[outIndex];
            encodedData.position(info.offset);
            encodedData.limit(info.offset + info.size);

            // Você deve adicionar ADTS se quiser salvar como .aac (opcional se você usar MediaMuxer)
            byte[] adts = addADTStoPacket(info.size + 7);

            byte[] aacData = new byte[info.size];
            encodedData.get(aacData);

            out.write(adts);         // cabeçalho ADTS
            out.write(aacData);  // dados AAC

            muxer.writeSampleData(false, encodedData, info);

            audioCodec.releaseOutputBuffer(outIndex, false);
        }
    }

    private byte[] addADTStoPacket(int packetLen) {
        byte[] packet = new byte[7];

        int profile = 2; // AAC LC
        int freqIdx = 4; // 44100 Hz
        int chanCfg = 2; // stereo

        packet[0] = (byte) 0xFF;
        packet[1] = (byte) 0xF9;
        packet[2] = (byte) (((profile - 1) << 6) + (freqIdx << 2) + (chanCfg >> 2));
        packet[3] = (byte) (((chanCfg & 3) << 6) + (packetLen >> 11));
        packet[4] = (byte) ((packetLen & 0x7FF) >> 3);
        packet[5] = (byte) (((packetLen & 7) << 5) + 0x1F);
        packet[6] = (byte) 0xFC;

        return packet;
    }


    public void stopInternalAudioCapture() {
        isCapturing = false;

        if (captureThread != null) {
            captureThread.interrupt();
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
