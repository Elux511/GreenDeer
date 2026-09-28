package com.it.greendeer.tts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WAV 工具测试：头结构合法性。
 */
class WavUtilTest {

    @Test
    void silenceWavHasValidHeader() {
        byte[] wav = WavUtil.silenceWav(16000, 1000);
        assertEquals("RIFF", ascii(wav, 0, 4));
        assertEquals("WAVE", ascii(wav, 8, 4));
        assertEquals("fmt ", ascii(wav, 12, 4));
        assertEquals("data", ascii(wav, 36, 4));
        // 单声道 16bit 16kHz：1s → 32000 字节 PCM
        assertEquals(32000, leInt(wav, 40));
        assertEquals(32000 + 44, wav.length);
        assertEquals(16000, leInt(wav, 24));
        assertEquals(1, leShort(wav, 22));
        assertEquals(16, leShort(wav, 34));
    }

    @Test
    void tickWavLengthsMatch() {
        byte[] wav = WavUtil.tickWav(8000, 500, 440, 100, 0.05);
        // 8000Hz mono16：0.5s = 4000 采样 × 2B = 8000B + 44B 头
        assertEquals(8000 * 500 / 1000 * 2 + 44, wav.length);
    }

    private static String ascii(byte[] b, int off, int len) {
        return new String(b, off, len, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static int leInt(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    private static int leShort(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }
}
