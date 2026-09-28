package com.it.greendeer.tts;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 /audio/speech 实现（tts-1 等）。默认输出 mp3。
 */
@Slf4j
@Component
public class OpenAiTtsProvider implements TtsProvider {

    private final AppProperties.Tts.OpenAi cfg;
    private final OkHttpClient client;

    public OpenAiTtsProvider(AppProperties props) {
        this.cfg = props.getTts().getOpenai();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
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
    public TtsResult synthesize(String text, String voice, int sampleRate) throws Exception {
        ObjectNode body = Json.obj();
        Json.put(body, "model", cfg.getModel());
        Json.put(body, "input", text);
        Json.put(body, "voice", voice == null || voice.isBlank() ? cfg.getVoice() : voice);
        Json.put(body, "response_format", cfg.getResponseFormat());
        String url = cfg.getBaseUrl().replaceAll("/+$", "") + "/audio/speech";
        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + cfg.getApiKey())
                .post(RequestBody.create(Json.write(body), MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String detail = response.body() == null ? "" : response.body().string();
                throw new BizException(502, "TTS 调用失败 HTTP " + response.code() + ": " + truncate(detail, 300));
            }
            byte[] audio = response.body() == null ? new byte[0] : response.body().bytes();
            String fmt = cfg.getResponseFormat().toLowerCase(Locale.ROOT);
            String mime = "wav".equals(fmt) ? "audio/wav" : "audio/mpeg";
            return new TtsResult(audio, mime, 24000, 0, name());
        } catch (IOException e) {
            throw new BizException(502, "TTS 流式请求失败: " + e.getMessage());
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
