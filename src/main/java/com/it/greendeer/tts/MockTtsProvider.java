package com.it.greendeer.tts;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Mock TTS：无任何外部依赖。
 * 输出"提示音 + 静音"的合法 wav（演示闭环下能感知句子推送，且占位时长接近真人语速）。
 * 时长按 90ms/字 估算。
 */
@Component
public class MockTtsProvider implements TtsProvider {

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public TtsResult synthesize(String text, String voice, int sampleRate) {
        int sr = sampleRate > 0 ? sampleRate : 16000;
        int durationMs = Math.max(200, Math.min(8000, text.length() * 90));
        byte[] wav = "beep".equalsIgnoreCase(voice)
                ? WavUtil.tickWav(sr, durationMs, 880, 60, 0.06)
                : WavUtil.tickWav(sr, durationMs, 440, 40, 0.05);
        return TtsResult.wav(wav, sr, durationMs, name());
    }
}
