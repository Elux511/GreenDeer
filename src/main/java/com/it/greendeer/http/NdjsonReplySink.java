package com.it.greendeer.http;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.chat.ReplySink;
import com.it.greendeer.common.Json;
import com.it.greendeer.tts.WavUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * DH_live dialog.js 兼容的 NDJSON 输出通道（每行一个 JSON 块）：
 * {"text":"...","audio":"<base64 wav>","endpoint":true|false}
 *
 * <p>参考 DH_live web_demo/server.py 的 /eb_stream 返回协议。
 */
public class NdjsonReplySink implements ReplySink {

    private final OutputStream out;
    private boolean closed = false;

    public NdjsonReplySink(OutputStream out) {
        this.out = out;
    }

    @Override
    public void start(long sessionId, long replyId) {
        // NDJSON 协议无 start 帧
    }

    @Override
    public void sentence(long sessionId, long replyId, String text, boolean endpoint,
                         byte[] audio, String mime, int durationMs, boolean first) {
        ObjectNode chunk = Json.obj();
        Json.put(chunk, "text", text);
        Json.put(chunk, "audio", audio == null || audio.length == 0
                ? Base64.getEncoder().encodeToString(WavUtil.silenceWav(16000, 200))
                : Base64.getEncoder().encodeToString(audio));
        Json.put(chunk, "endpoint", endpoint);
        write(chunk);
    }

    @Override
    public void error(long sessionId, long replyId, String message) {
        ObjectNode chunk = Json.obj();
        Json.put(chunk, "text", "（服务暂时不可用，请稍后再试～）");
        Json.put(chunk, "audio", Base64.getEncoder().encodeToString(WavUtil.silenceWav(16000, 200)));
        Json.put(chunk, "endpoint", true);
        write(chunk);
    }

    private void write(ObjectNode chunk) {
        if (closed) {
            return;
        }
        try {
            out.write(Json.write(chunk).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            closed = true; // 客户端断开，停止写入
        }
    }

    public boolean isClosed() {
        return closed;
    }
}
