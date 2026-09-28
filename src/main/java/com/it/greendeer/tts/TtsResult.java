package com.it.greendeer.tts;

/**
 * TTS 合成结果。
 *
 * @param audio       音频字节（wav 或 mp3，见 mime）
 * @param mime        音频 MIME：audio/wav | audio/mpeg
 * @param sampleRate  采样率(Hz)
 * @param durationMs  估算时长(ms)
 * @param provider    实际使用的提供商名称
 */
public record TtsResult(byte[] audio, String mime, int sampleRate, int durationMs, String provider) {

    public static TtsResult wav(byte[] audio, int sampleRate, int durationMs, String provider) {
        return new TtsResult(audio, "audio/wav", sampleRate, durationMs, provider);
    }
}
