package com.it.greendeer.ai.llm;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * 大模型提供商抽象。streamReply 的 onDelta 会在"读取线程"上被顺序回调，
 * 调用方可以直接在回调里同步消费增量文本（分句 + TTS + 推送），
 * 顺序由单连接串行读取保证。
 */
public interface LlmProvider {

    /** 提供商标识：mock / openai。 */
    String name();

    /** 当前配置下是否可用（openai 需要 api-key）。 */
    boolean available();

    /**
     * 流式对话。
     *
     * @param systemPrompt 人设 system prompt
     * @param history      历史轮次(user/assistant 交错)
     * @param cancel       取消令牌，置 true 后尽快终止
     * @param onDelta      增量文本回调（顺序串行）
     */
    void streamReply(String systemPrompt, List<ChatTurn> history, AtomicBoolean cancel, Consumer<String> onDelta)
            throws Exception;
}
