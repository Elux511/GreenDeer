package com.it.greendeer.tts;

/**
 * WAV(PCM16 单声道) 工具：拼 44 字节头 + 生成静音/短提示音数据，供 mock TTS 与测试使用。
 */
public final class WavUtil {

    private WavUtil() {
    }

    /** 由 PCM16 小端数据构造完整 wav 字节。 */
    public static byte[] wrapPcm16(byte[] pcm, int sampleRate) {
        int dataLen = pcm.length;
        int byteRate = sampleRate * 2;
        byte[] wav = new byte[44 + dataLen];
        putAscii(wav, 0, "RIFF");
        putIntLe(wav, 4, 36 + dataLen);
        putAscii(wav, 8, "WAVE");
        putAscii(wav, 12, "fmt ");
        putIntLe(wav, 16, 16);
        putShortLe(wav, 20, (short) 1);       // PCM
        putShortLe(wav, 22, (short) 1);       // mono
        putIntLe(wav, 24, sampleRate);
        putIntLe(wav, 28, byteRate);
        putShortLe(wav, 32, (short) 2);       // block align
        putShortLe(wav, 34, (short) 16);      // bits
        putAscii(wav, 36, "data");
        putIntLe(wav, 40, dataLen);
        System.arraycopy(pcm, 0, wav, 44, dataLen);
        return wav;
    }

    /** 生成 durationMs 毫秒的静音 wav。 */
    public static byte[] silenceWav(int sampleRate, int durationMs) {
        int samples = (int) (sampleRate * durationMs / 1000.0);
        return wrapPcm16(new byte[samples * 2], sampleRate);
    }

    /** 生成：开头一段低音量短提示音 + 剩余静音（演示时能听出"句子边界"）。 */
    public static byte[] tickWav(int sampleRate, int totalMs, int toneHz, int toneMs, double amplitude) {
        int total = (int) (sampleRate * totalMs / 1000.0);
        int tone = Math.min(total, (int) (sampleRate * toneMs / 1000.0));
        byte[] pcm = new byte[total * 2];
        double step = 2 * Math.PI * toneHz / sampleRate;
        for (int i = 0; i < tone; i++) {
            short s = (short) (Math.sin(step * i) * amplitude * Short.MAX_VALUE);
            pcm[i * 2] = (byte) (s & 0xff);
            pcm[i * 2 + 1] = (byte) ((s >> 8) & 0xff);
        }
        return wrapPcm16(pcm, sampleRate);
    }

    // ------------------------------------------------------------------ //

    private static void putAscii(byte[] b, int off, String s) {
        for (int i = 0; i < s.length(); i++) {
            b[off + i] = (byte) s.charAt(i);
        }
    }

    private static void putIntLe(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
        b[off + 2] = (byte) ((v >> 16) & 0xff);
        b[off + 3] = (byte) ((v >> 24) & 0xff);
    }

    private static void putShortLe(byte[] b, int off, short v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >> 8) & 0xff);
    }
}
