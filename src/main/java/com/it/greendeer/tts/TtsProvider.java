package com.it.greendeer.tts;

/**
 * TTS 提供商抽象。
 */
public interface TtsProvider {

    /** 提供商标识：mock | sherpa-http | openai。 */
    String name();

    /** 当前配置下是否可用。 */
    boolean available();

    /**
     * 合成整段文本。
     *
     * @param text       文本
     * @param voice      音色名（提供商内部解析；可为空走默认）
     * @param sampleRate 期望采样率（部分引擎不支持时取引擎默认）
     */
    TtsResult synthesize(String text, String voice, int sampleRate) throws Exception;
}
