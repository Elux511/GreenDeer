package com.it.greendeer.live;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * WebSocket 订阅中枢：按 sessionId 维护连接，供 AI 互动链路广播帧。
 */
@Slf4j
@Component
public class LiveWsHub {

    private final Map<Long, Set<WebSocketSession>> subscribers = new ConcurrentHashMap<>();

    public void subscribe(Long sessionId, WebSocketSession session) {
        subscribers.computeIfAbsent(sessionId, k -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unsubscribe(Long sessionId, WebSocketSession session) {
        Set<WebSocketSession> set = subscribers.get(sessionId);
        if (set != null) {
            set.remove(session);
            if (set.isEmpty()) {
                subscribers.remove(sessionId);
            }
        }
    }

    public int subscriberCount(Long sessionId) {
        Set<WebSocketSession> set = subscribers.get(sessionId);
        return set == null ? 0 : set.size();
    }

    /** 向所有场次的全部连接广播（OBS 事件等全局状态用）。 */
    public void broadcastAll(String json) {
        TextMessage msg = new TextMessage(json);
        for (Map.Entry<Long, Set<WebSocketSession>> e : subscribers.entrySet()) {
            for (WebSocketSession s : e.getValue()) {
                if (!s.isOpen()) {
                    unsubscribe(e.getKey(), s);
                    continue;
                }
                try {
                    synchronized (s) {
                        s.sendMessage(msg);
                    }
                } catch (IOException | IllegalStateException ex) {
                    unsubscribe(e.getKey(), s);
                }
            }
        }
    }

    /** 向某场次的所有连接广播一帧 JSON。坏连接自动剔除。 */
    public void push(Long sessionId, String json) {
        Set<WebSocketSession> set = subscribers.get(sessionId);
        if (set == null || set.isEmpty()) {
            return;
        }
        TextMessage msg = new TextMessage(json);
        for (WebSocketSession s : set) {
            if (!s.isOpen()) {
                unsubscribe(sessionId, s);
                continue;
            }
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("推送失败 session={} err={}", sessionId, e.getMessage());
                unsubscribe(sessionId, s);
            }
        }
    }
}
