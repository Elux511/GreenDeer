package com.it.greendeer.chat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.message.ChatMessage;
import com.it.greendeer.domain.message.ChatMessageRepository;
import com.it.greendeer.domain.message.MessageType;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.domain.session.LiveSessionRepository;
import com.it.greendeer.domain.session.SessionStatus;
import com.it.greendeer.live.LiveWsHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 弹幕/评论 AI 互动服务：入站弹幕 → 落库 → 广播 → 投喂给串行 worker 触发 AI 回复。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InteractiveChatService {

    private final LiveSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatCoordinator coordinator;
    private final LiveWsHub hub;
    private final AppProperties props;

    /**
     * 入站弹幕（平台网关 / WS / 模拟器统一走这里）。
     *
     * @return 落库的弹幕消息；内容为空返回 null
     */
    @Transactional
    public ChatMessage handleInboundComment(Long sessionId, String userName, String content) {
        LiveSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(404, "场次不存在: " + sessionId));
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BizException(409, "场次已结束，无法接收弹幕");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            return null;
        }
        int maxLen = props.getChat().getMaxCommentLength();
        if (text.length() > maxLen) {
            text = text.substring(0, maxLen);
        }
        String nick = (userName == null || userName.isBlank()) ? "观众" : userName.trim();
        if (nick.length() > 32) {
            nick = nick.substring(0, 32);
        }

        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setType(MessageType.COMMENT);
        msg.setUserName(nick);
        msg.setContent(text);
        msg = messageRepository.save(msg);

        ObjectNode frame = Json.obj();
        Json.put(frame, "type", "comment");
        Json.put(frame, "sessionId", sessionId);
        Json.put(frame, "userName", nick);
        Json.put(frame, "content", text);
        Json.put(frame, "messageId", msg.getId());
        hub.push(sessionId, Json.write(frame));

        // 仅 LIVE 且开启自动回复才触发 AI
        if (session.isAutoReply() && session.getStatus() == SessionStatus.LIVE) {
            CommentTask task = new CommentTask(msg.getId(), nick, text);
            boolean accepted = coordinator.enqueue(sessionId, task);
            if (!accepted) {
                log.warn("弹幕队列已满，丢弃 session={} content={}", sessionId, text);
                ObjectNode warn = Json.obj();
                Json.put(warn, "type", "error");
                Json.put(warn, "message", "弹幕过多，本条已忽略");
                Json.put(warn, "messageId", msg.getId());
                hub.push(sessionId, Json.write(warn));
            }
        }
        return msg;
    }

    /** 中断当前 AI 回复。 */
    public void interrupt(Long sessionId) {
        sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BizException(404, "场次不存在: " + sessionId));
        coordinator.interrupt(sessionId);
        ObjectNode frame = Json.obj();
        Json.put(frame, "type", "session_event");
        Json.put(frame, "sessionId", sessionId);
        Json.put(frame, "event", "reply_interrupted");
        Json.put(frame, "message", "已中断当前 AI 回复");
        hub.push(sessionId, Json.write(frame));
    }
}
