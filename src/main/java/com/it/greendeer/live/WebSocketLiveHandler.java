package com.it.greendeer.live;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.chat.InteractiveChatService;
import com.it.greendeer.common.Json;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;

/**
 * /ws/live/{sessionId} 控制台/数字人页实时通道。
 *
 * <p>下行帧（JSON）：
 * <ul>
 *   <li>{"type":"comment","sessionId":..,"userName":"..","content":"..","messageId":..}</li>
 *   <li>{"type":"reply_start","sessionId":..,"replyId":..}</li>
 *   <li>{"type":"reply_chunk","sessionId":..,"replyId":..,"text":"..","audio":"base64","mime":"..","endpoint":false}</li>
 *   <li>{"type":"session_event","sessionId":..,"event":"started|paused|ended","message":".."}</li>
 *   <li>{"type":"obs_event",...} / {"type":"error","message":".."}</li>
 * </ul>
 * 上行帧：{"type":"comment","userName":"..","content":".."} 或 {"type":"ping"}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketLiveHandler extends TextWebSocketHandler {

    private final LiveWsHub hub;
    private final InteractiveChatService chatService;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long sessionId = parseSessionId(session.getUri());
        if (sessionId == null) {
            log.warn("WS 拒绝：无效场次ID，uri={}", session.getUri());
            session.close(CloseStatus.POLICY_VIOLATION.withReason("无效的场次ID"));
            return;
        }
        session.getAttributes().put("sessionId", sessionId);
        hub.subscribe(sessionId, session);
        log.debug("WS 已订阅 session={} 连接数={}", sessionId, hub.subscriberCount(sessionId));
        // 注意：这里不再推 viewer_joined，避免触发前端"收到事件→重连→再收事件"的连接风暴
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long sessionId = (Long) session.getAttributes().get("sessionId");
        if (sessionId == null) {
            return;
        }
        JsonNode jo;
        try {
            jo = Json.parse(message.getPayload());
        } catch (Exception e) {
            return;
        }
        String type = Json.text(jo, "type");
        if ("ping".equals(type)) {
            ObjectNode pong = Json.obj();
            Json.put(pong, "type", "pong");
            session.sendMessage(new TextMessage(Json.write(pong)));
            return;
        }
        if ("comment".equals(type)) {
            String userName = Json.text(jo, "userName");
            String content = Json.text(jo, "content");
            if (userName == null || userName.isBlank()) {
                userName = "观众";
            }
            if (content != null && !content.isBlank()) {
                chatService.handleInboundComment(sessionId, userName, content);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long sessionId = (Long) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            hub.unsubscribe(sessionId, session);
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        Long sessionId = (Long) session.getAttributes().get("sessionId");
        if (sessionId != null) {
            hub.unsubscribe(sessionId, session);
        }
    }

    private Long parseSessionId(URI uri) {
        if (uri == null) {
            return null;
        }
        String path = uri.getPath();
        String tail = path.substring(path.lastIndexOf('/') + 1);
        try {
            return Long.parseLong(tail);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
