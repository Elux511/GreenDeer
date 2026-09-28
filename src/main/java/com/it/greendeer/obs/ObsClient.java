package com.it.greendeer.obs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * obs-websocket v5 客户端（JSON-RPC over WebSocket）。
 *
 * <p>握手：server Hello(op0, 含 challenge/salt) → client Identify(op1, 携带 SHA256 双重摘要鉴权)
 * → server Identified(op2)。业务请求 op6，响应 op7。
 */
@Slf4j
public class ObsClient {

    private static final int OP_HELLO = 0;
    private static final int OP_IDENTIFY = 1;
    private static final int OP_IDENTIFIED = 2;
    private static final int OP_EVENT = 5;
    private static final int OP_REQUEST = 6;
    private static final int OP_RESPONSE = 7;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)   // WS 长连接不设读超时
            .build();

    private volatile WebSocket ws;
    private volatile boolean connected = false;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final List<Consumer<JsonNode>> eventListeners = new CopyOnWriteArrayList<>();

    /** 同步连接并完成鉴权。 */
    public synchronized void connect(String host, int port, String password, int timeoutSec) throws Exception {
        disconnect();
        CompletableFuture<Void> identified = new CompletableFuture<>();
        String url = "ws://" + host + ":" + port;
        Request request = new Request.Builder().url(url).build();
        this.ws = http.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                log.info("OBS WebSocket 已连接: {}", url);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                handleMessage(text, identified, password);
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                if (!identified.isDone()) {
                    identified.completeExceptionally(new BizException(400, "OBS 连接被关闭(code=" + code + "): " + reason));
                }
                onDisconnect("连接关闭: " + reason);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                onDisconnect(t.getMessage());
                identified.completeExceptionally(new BizException(400, "无法连接 OBS(" + url + "): " + t.getMessage()));
            }
        });
        try {
            identified.get(timeoutSec, TimeUnit.SECONDS);
            connected = true;
            log.info("OBS 连接并鉴权成功: {}:{}", host, port);
        } catch (TimeoutException e) {
            disconnect();
            throw new BizException(400, "OBS 握手超时(" + timeoutSec + "s)：TCP 已连通但对方未完成 obs-websocket v5 鉴权。"
                    + "请检查：1) OBS 版本是否 ≥28(内置 v5，旧版 obs-websocket 4.x 插件协议不兼容)；"
                    + "2) OBS 菜单 工具→WebSocket 服务器设置 是否已勾选启用；"
                    + "3) 端口(默认 4455)是否被其它程序占用，可用 netstat -ano | findstr :4455 确认监听进程为 obs64.exe；"
                    + "4) 密码与本系统配置(application.yml app.obs.* 或控制台 OBS 面板)是否一致；"
                    + "5) 若 OBS 弹出连接授权窗口，请点击允许。");
        } catch (Exception e) {
            disconnect();
            throw new BizException(400, "OBS 鉴权失败: " + rootMessage(e));
        }
    }

    public synchronized void disconnect() {
        WebSocket old = ws;
        ws = null;
        connected = false;
        if (old != null) {
            old.close(1000, "bye");
        }
        failAllPending("OBS 连接已断开");
    }

    public boolean isConnected() {
        return connected && ws != null;
    }

    /** 发送请求并等待响应（同步），返回 responseData。 */
    public JsonNode request(String requestType, JsonNode requestData, int timeoutSec) {
        if (!isConnected()) {
            throw new BizException(409, "OBS 未连接，请先 /api/v1/obs/connect");
        }
        String rid = UUID.randomUUID().toString();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(rid, future);
        try {
            ObjectNode d = Json.obj();
            Json.put(d, "requestType", requestType);
            Json.put(d, "requestId", rid);
            if (requestData != null) {
                d.set("requestData", requestData);
            }
            ObjectNode frame = Json.obj();
            Json.put(frame, "op", OP_REQUEST);
            frame.set("d", d);
            if (!ws.send(Json.write(frame))) {
                throw new BizException(502, "OBS 发送请求失败(连接可能已断开)");
            }
            JsonNode resp = future.get(timeoutSec, TimeUnit.SECONDS);
            if (resp == null) {
                throw new BizException(502, "OBS 无响应: " + requestType);
            }
            String err = requestError(resp);
            if (err != null) {
                throw new BizException(502, "OBS " + requestType + " 失败: " + err);
            }
            return resp.get("responseData");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(502, "OBS 请求异常 " + requestType + ": " + rootMessage(e));
        } finally {
            pending.remove(rid);
        }
    }

    public void addEventListener(Consumer<JsonNode> listener) {
        eventListeners.add(listener);
    }

    /**
     * 解析 obs-websocket 响应状态：成功返回 null，失败返回可读错误。
     *
     * <p>v5 协议的状态在 d.requestStatus 对象中（{result, code, comment}），
     * 旧版 v4 风格才是顶层 status 字符串；两者都兼容。
     */
    static String requestError(JsonNode resp) {
        if (resp == null) {
            return "空响应";
        }
        JsonNode rs = resp.get("requestStatus");
        if (rs != null) {
            if (rs.path("result").asBoolean(false)) {
                return null;
            }
            int code = rs.path("code").asInt(-1);
            String comment = rs.path("comment").asText("");
            return "code=" + code + (comment.isBlank() ? "" : ", " + comment);
        }
        // 兼容旧式 v4 风格
        String status = Json.text(resp, "status");
        if ("ok".equals(status)) {
            return null;
        }
        String comment = Json.text(resp, "comment");
        return comment == null ? String.valueOf(status) : comment;
    }

    // ------------------------------------------------------------------ //

    private void handleMessage(String text, CompletableFuture<Void> identified, String password) {
        JsonNode msg;
        try {
            msg = Json.parse(text);
        } catch (Exception e) {
            return;
        }
        int op = msg.path("op").asInt(-1);
        JsonNode d = msg.get("d");
        if (d == null) {
            return;
        }
        switch (op) {
            case OP_HELLO -> {
                JsonNode auth = d.get("authentication");
                ObjectNode identify = Json.obj();
                Json.put(identify, "rpcVersion", 1);
                Json.put(identify, "eventSubscriptions", 33);
                if (auth != null) {
                    String challenge = Json.text(auth, "challenge");
                    String salt = Json.text(auth, "salt");
                    Json.put(identify, "authentication", computeAuth(password, challenge, salt));
                }
                ObjectNode frame = Json.obj();
                Json.put(frame, "op", OP_IDENTIFY);
                frame.set("d", identify);
                send(frame);
            }
            case OP_IDENTIFIED -> identified.complete(null);
            case OP_EVENT -> {
                ObjectNode event = Json.obj();
                Json.put(event, "eventType", Json.text(d, "eventType"));
                JsonNode eventData = d.get("eventData");
                event.set("eventData", eventData == null ? Json.obj() : eventData);
                for (Consumer<JsonNode> l : eventListeners) {
                    try {
                        l.accept(event);
                    } catch (Exception ignore) {
                    }
                }
            }
            case OP_RESPONSE -> {
                String rid = Json.text(d, "requestId");
                if (rid != null) {
                    CompletableFuture<JsonNode> f = pending.get(rid);
                    if (f != null) {
                        f.complete(d);
                    }
                }
            }
            default -> log.debug("OBS 未知 op: {}", op);
        }
    }

    private void send(ObjectNode frame) {
        WebSocket w = ws;
        if (w != null) {
            w.send(Json.write(frame));
        }
    }

    private void onDisconnect(String reason) {
        if (connected || ws != null) {
            connected = false;
            log.warn("OBS 连接断开: {}", reason);
            failAllPending(reason);
            ObjectNode event = Json.obj();
            Json.put(event, "eventType", "ConnectionClosed");
            ObjectNode data = Json.obj();
            Json.put(data, "reason", reason);
            event.set("eventData", data);
            for (Consumer<JsonNode> l : eventListeners) {
                try {
                    l.accept(event);
                } catch (Exception ignore) {
                }
            }
        }
        ws = null;
    }

    private void failAllPending(String reason) {
        for (Map.Entry<String, CompletableFuture<JsonNode>> e : pending.entrySet()) {
            e.getValue().completeExceptionally(new BizException(502, reason));
        }
        pending.clear();
    }

    /** OBS v5 鉴权：secret=SHA256(password+salt)；auth=SHA256(secret+challenge)，均 base64。 */
    private static String computeAuth(String password, String challenge, String salt) {
        try {
            String secret = base64(sha256((password + salt).getBytes(StandardCharsets.UTF_8)));
            return base64(sha256((secret + challenge).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("OBS 鉴权计算失败", e);
        }
    }

    private static byte[] sha256(byte[] in) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(in);
    }

    private static String base64(byte[] in) {
        return Base64.getEncoder().encodeToString(in);
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null) {
            cur = cur.getCause();
        }
        String msg = cur.getMessage();
        return msg == null ? cur.getClass().getSimpleName() : msg;
    }
}
