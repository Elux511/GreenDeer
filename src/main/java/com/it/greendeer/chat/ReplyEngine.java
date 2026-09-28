package com.it.greendeer.chat;

import com.it.greendeer.ai.chat.SentenceStream;
import com.it.greendeer.ai.llm.ChatTurn;
import com.it.greendeer.ai.llm.LlmProvider;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.message.ChatMessage;
import com.it.greendeer.domain.message.ChatMessageRepository;
import com.it.greendeer.domain.message.MessageType;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.tts.TtsResult;
import com.it.greendeer.tts.TtsSynthesizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 回复引擎：LLM 流式 → 分句 → TTS → 输出通道(WS/NDJSON)，并负责上下文组装与落库。
 *
 * <p>onDelta 由 LLM 读取线程顺序回调，因此整段处理天然串行、有序；句子在回调内同步完成
 * TTS 与推送，避免乱序。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyEngine {

    private static final AtomicLong SEQ = new AtomicLong(1);

    private final ChatMessageRepository chatMessageRepository;
    private final AppProperties props;
    private final TtsSynthesizer tts;
    private final AudioStore audioStore;
    private final List<LlmProvider> llmProviders;

    /**
     * 执行一次完整回复。
     *
     * @param session 场次（eb_stream 场景可为未持久化的临时场次）
     * @param task    弹幕任务
     * @param sink    输出通道
     * @param cancel  取消令牌
     * @param persist 是否把 AI 回复写入消息库（WS 链路为 true）
     */
    public long runReply(LiveSession session, CommentTask task, ReplySink sink, AtomicBoolean cancel, boolean persist) {
        long sessionId = session.getId() == null ? 0L : session.getId();
        long replyId = SEQ.getAndIncrement();

        LlmProvider llm = pickLlm();
        String system = (session.getPersona() != null && !session.getPersona().isBlank())
                ? session.getPersona() : props.getChat().getDefaultPersona();
        List<ChatTurn> history = buildHistory(session, task.commentId());
        history.add(ChatTurn.user(task.content()));

        String prefix = session.getReplyPrefix() == null ? "" : session.getReplyPrefix().replace("{name}", task.userName());

        sink.start(sessionId, replyId);

        SentenceStream stream = new SentenceStream();
        StringBuilder full = new StringBuilder();
        int[] chunkIdx = {0};
        long[] totalMs = {0};
        boolean[] prefixDone = {false};
        boolean[] emittedAny = {false};

        try {
            llm.streamReply(system, history, cancel, delta -> {
                if (cancel.get() || delta == null || delta.isEmpty()) {
                    return;
                }
                full.append(delta);
                for (String sentence : stream.feed(delta)) {
                    emitSentence(sessionId, replyId, prefix, chunkIdx, totalMs, prefixDone, emittedAny,
                            sentence, false, sink, session);
                }
            });
            // 流结束：flush 残余，末句标记 endpoint
            for (String sentence : stream.flush()) {
                emitSentence(sessionId, replyId, prefix, chunkIdx, totalMs, prefixDone, emittedAny,
                        sentence, true, sink, session);
            }
            if (!emittedAny[0]) {
                String fallback = props.getChat().getReplyFallback();
                if (fallback == null || fallback.isBlank()) {
                    fallback = "收到啦，欢迎继续跟我聊天～";
                }
                emitSentence(sessionId, replyId, prefix, chunkIdx, totalMs, prefixDone, emittedAny,
                        fallback, true, sink, session);
            }
            if (persist && session.getId() != null) {
                persistReply(session.getId(), task, full.toString(), (int) totalMs[0]);
            }
        } catch (Exception e) {
            log.warn("AI 回复异常 session={} replyId={}: {}", sessionId, replyId, e.getMessage());
            sink.error(sessionId, replyId, "AI 回复失败: " + e.getMessage());
            if (persist && session.getId() != null && full.length() == 0) {
                persistReply(session.getId(), task, props.getChat().getReplyFallback(), 0);
            }
        }
        return replyId;
    }

    // ------------------------------------------------------------------ //

    private void emitSentence(long sessionId, long replyId, String prefix, int[] chunkIdx, long[] totalMs,
                              boolean[] prefixDone, boolean[] emittedAny, String sentence, boolean endpoint,
                              ReplySink sink, LiveSession session) {
        if (sentence == null || sentence.isBlank()) {
            return;
        }
        String text = sentence;
        if (!prefixDone[0] && !prefix.isEmpty()) {
            text = prefix + text;
            prefixDone[0] = true;
        }
        TtsResult res;
        try {
            res = tts.synthesize(text, session.getTtsVoice());
        } catch (Exception e) {
            log.warn("TTS 异常被忽略: {}", e.getMessage());
            res = null;
        }
        byte[] audio = res == null ? new byte[0] : res.audio();
        String mime = res == null ? "audio/wav" : res.mime();
        int dur = res == null ? 0 : res.durationMs();
        if (audio.length > 0) {
            saveAudio(sessionId, replyId, chunkIdx[0], audio, mime);
        }
        totalMs[0] += dur;
        sink.sentence(sessionId, replyId, text, endpoint, audio, mime, dur, !emittedAny[0]);
        emittedAny[0] = true;
        chunkIdx[0]++;
    }

    private void saveAudio(long sessionId, long replyId, int idx, byte[] audio, String mime) {
        try {
            String ext = mime != null && mime.contains("wav") ? "wav" : "mp3";
            String folder = sessionId == 0L ? "eb" : String.valueOf(sessionId);
            audioStore.save(folder, "reply-" + replyId + "-" + idx + "." + ext, audio);
        } catch (Exception e) {
            log.warn("音频保存失败: {}", e.getMessage());
        }
    }

    private void persistReply(Long sessionId, CommentTask task, String fullText, int durationMs) {
        if (fullText == null || fullText.isBlank()) {
            return;
        }
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setType(MessageType.REPLY);
        msg.setUserName("AI主播");
        msg.setContent(fullText.trim());
        msg.setReplyToId(task.commentId());
        msg.setAudioDurationMs(durationMs);
        chatMessageRepository.save(msg);
    }

    /** 组装 system 之外的历史轮次（该弹幕之前），保证 role 交替。 */
    private List<ChatTurn> buildHistory(LiveSession session, Long beforeCommentId) {
        int cap = session.getMaxHistory() != null && session.getMaxHistory() > 0
                ? session.getMaxHistory() : props.getChat().getMaxHistory();
        List<ChatMessage> recent = (beforeCommentId == null || session.getId() == null)
                ? List.of()
                : chatMessageRepository.findBySessionIdAndIdLessThanOrderByIdDesc(session.getId(), beforeCommentId,
                PageRequest.of(0, cap, Sort.by(Sort.Direction.DESC, "id")));

        List<ChatTurn> turns = new ArrayList<>();
        for (int i = recent.size() - 1; i >= 0; i--) {   // 反转为时间正序
            ChatMessage m = recent.get(i);
            String role = switch (m.getType()) {
                case COMMENT -> ChatTurn.ROLE_USER;
                case REPLY -> ChatTurn.ROLE_ASSISTANT;
                case SYSTEM -> null;
            };
            if (role == null) {
                continue;
            }
            String content = m.getContent();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            if (content.isBlank()) {
                continue;
            }
            if (!turns.isEmpty() && turns.get(turns.size() - 1).role().equals(role)) {
                ChatTurn prev = turns.remove(turns.size() - 1);
                turns.add(new ChatTurn(role, prev.content() + content));
            } else {
                turns.add(new ChatTurn(role, content));
            }
        }
        return turns;
    }

    private LlmProvider pickLlm() {
        Map<String, LlmProvider> byName = new HashMap<>();
        for (LlmProvider p : llmProviders) {
            byName.put(p.name(), p);
        }
        String want = props.getLlm().getProvider();
        LlmProvider p = byName.get(want);
        if (p != null && p.available()) {
            return p;
        }
        if (p != null && !p.available()) {
            log.warn("LLM provider '{}' 未配置 API Key，降级到 mock", want);
        }
        LlmProvider mock = byName.get("mock");
        return mock != null ? mock : llmProviders.get(0);
    }
}
