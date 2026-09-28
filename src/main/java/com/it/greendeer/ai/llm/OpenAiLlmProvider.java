package com.it.greendeer.ai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * OpenAI 兼容 /chat/completions 流式实现（DeepSeek、豆包 Ark、OpenAI 等均可）。
 * 逐行解析 SSE："data: {...}"，delta.content 非空即回调。
 */
@Slf4j
@Component
public class OpenAiLlmProvider implements LlmProvider {

    private final AppProperties.Llm.OpenAi cfg;
    private final OkHttpClient client;

    public OpenAiLlmProvider(AppProperties props) {
        this.cfg = props.getLlm().getOpenai();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String name() {
        return "openai";
    }

    @Override
    public boolean available() {
        return cfg.getApiKey() != null && !cfg.getApiKey().isBlank();
    }

    @Override
    public void streamReply(String systemPrompt, List<ChatTurn> history, AtomicBoolean cancel, Consumer<String> onDelta)
            throws Exception {
        ArrayNode messages = Json.arr();
        messages.add(Json.put(Json.obj(), "role", ChatTurn.ROLE_SYSTEM).put("content", systemPrompt));
        for (ChatTurn turn : history) {
            if (!turn.content().isBlank()) {
                ObjectNode m = Json.obj();
                Json.put(m, "role", turn.role());
                Json.put(m, "content", turn.content());
                messages.add(m);
            }
        }
        ObjectNode body = Json.obj();
        Json.put(body, "model", cfg.getModel());
        Json.put(body, "messages", messages);
        Json.put(body, "stream", true);
        Json.put(body, "temperature", cfg.getTemperature());
        Json.put(body, "max_tokens", cfg.getMaxTokens());

        String url = cfg.getBaseUrl().replaceAll("/+$", "") + "/chat/completions";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + cfg.getApiKey())
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(Json.write(body), MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String detail = response.body() == null ? "" : response.body().string();
                throw new BizException(502, "LLM 调用失败 HTTP " + response.code() + ": " + truncate(detail, 300));
            }
            ResponseBody rb = response.body();
            if (rb == null) {
                throw new BizException(502, "LLM 返回空响应");
            }
            okio.BufferedSource source = rb.source();
            String line;
            while ((line = source.readUtf8Line()) != null) {
                if (cancel.get()) {
                    source.close();
                    return;
                }
                if (line.isBlank() || !line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty() || "[DONE]".equals(payload)) {
                    continue;
                }
                String content = extractDelta(payload);
                if (content != null && !content.isEmpty()) {
                    onDelta.accept(content);
                }
            }
        } catch (IOException e) {
            throw new BizException(502, "LLM 流式读取失败: " + e.getMessage());
        }
    }

    /** 兼容两种常见结构：choices[0].delta.content 或 choices[0].text。 */
    private String extractDelta(String payload) {
        try {
            JsonNode root = Json.parse(payload);
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode choice = choices.get(0);
            JsonNode delta = choice.get("delta");
            if (delta != null && delta.get("content") != null && delta.get("content").isTextual()) {
                return delta.get("content").asText();
            }
            JsonNode text = choice.get("text");
            return text != null && text.isTextual() ? text.asText() : null;
        } catch (Exception e) {
            log.warn("LLM SSE 解析失败，忽略该行: {}", truncate(payload, 120));
            return null;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
