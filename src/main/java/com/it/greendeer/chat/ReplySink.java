package com.it.greendeer.chat;

/**
 * 一条 AI 回复的输出通道：WS 推送 / NDJSON(eb_stream) / 测试收集器 各自实现。
 */
public interface ReplySink {

    /** 回复开始。 */
    void start(long sessionId, long replyId);

    /**
     * 输出一个完整句子（含音频）。
     *
     * @param sessionId  场次 id
     * @param replyId    回复序号
     * @param text       句子文本
     * @param endpoint   是否为最后一句
     * @param audio      音频字节(可为空)
     * @param mime       音频 MIME
     * @param durationMs 音频时长(ms)
     * @param first      是否为第一句
     */
    void sentence(long sessionId, long replyId, String text, boolean endpoint,
                  byte[] audio, String mime, int durationMs, boolean first);

    /** 回复异常。 */
    void error(long sessionId, long replyId, String message);
}
