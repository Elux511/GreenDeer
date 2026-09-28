package com.it.greendeer.chat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.Json;
import com.it.greendeer.live.LiveWsHub;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Base64;

/**
 * WS 输出通道：把 AI 回复帧广播给场次的所有订阅连接。
 */
@Component
@RequiredArgsConstructor
public class WsReplySink implements ReplySink {

    private final LiveWsHub hub;

    @Override
    public void start(long sessionId, long replyId) {
        ObjectNode frame = Json.obj();
        Json.put(frame, "type", "reply_start");
        Json.put(frame, "sessionId", sessionId);
        Json.put(frame, "replyId", replyId);
        hub.push(sessionId, Json.write(frame));
    }

    @Override
    public void sentence(long sessionId, long replyId, String text, boolean endpoint,
                         byte[] audio, String mime, int durationMs, boolean first) {
        ObjectNode frame = Json.obj();
        Json.put(frame, "type", first && !endpoint ? "reply_start" : "reply_chunk");
        Json.put(frame, "sessionId", sessionId);
        Json.put(frame, "replyId", replyId);
        Json.put(frame, "text", text);
        Json.put(frame, "endpoint", endpoint);
        Json.put(frame, "durationMs", durationMs);
        Json.put(frame, "mime", mime == null ? "audio/wav" : mime);
        if (audio != null && audio.length > 0) {
            Json.put(frame, "audio", Base64.getEncoder().encodeToString(audio));
        }
        hub.push(sessionId, Json.write(frame));
    }

    @Override
    public void error(long sessionId, long replyId, String message) {
        ObjectNode frame = Json.obj();
        Json.put(frame, "type", "reply_error");
        Json.put(frame, "sessionId", sessionId);
        Json.put(frame, "replyId", replyId);
        Json.put(frame, "message", message);
        hub.push(sessionId, Json.write(frame));
    }
}
