package com.it.greendeer.tts;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * 本地 sherpa-onnx TTS 网关(HTTP)。
 *
 * <p>配套网关脚本：tools/tts_gateway.py（FastAPI，若已安装则直接可用）。
 * 仅做 POST /tts 并返回 wav 二进制，Java 侧无需任何 Python 依赖知识。
 */
@Slf4j
@Component
public class HttpSherpaTtsProvider implements TtsProvider {

    private final AppProperties.Tts.Sherpa cfg;
    private final OkHttpClient client;

    public HttpSherpaTtsProvider(AppProperties props) {
        this.cfg = props.getTts().getSherpa();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(cfg.getTimeoutSeconds(), TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public String name() {
        return "sherpa-http";
    }

    @Override
    public boolean available() {
        return true; // 是否真的可用取决于网关是否在跑，运行时再判
    }

    @Override
    public TtsResult synthesize(String text, String voice, int sampleRate) throws Exception {
        ObjectNode body = Json.obj();
        Json.put(body, "text", text);
        Json.put(body, "voice", voice == null ? "" : voice);
        Json.put(body, "sample_rate", sampleRate);
        String url = cfg.getGatewayUrl().replaceAll("/+$", "") + "/tts";
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(Json.write(body), MediaType.get("application/json; charset=utf-8")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String detail = response.body() == null ? "" : response.body().string();
                throw new BizException(502, "本地 TTS 网关不可用(HTTP " + response.code() + "): " + truncate(detail, 200));
            }
            byte[] wav = response.body() == null ? new byte[0] : response.body().bytes();
            if (wav.length < 44) {
                throw new BizException(502, "本地 TTS 网关返回数据异常");
            }
            return TtsResult.wav(wav, sampleRate, estimateMs(wav), name());
        } catch (IOException e) {
            throw new BizException(502, "无法连接本地 TTS 网关 " + url + "：" + e.getMessage());
        }
    }

    /** 从 wav 头估算时长：data 块字节数 / byteRate。 */
    private int estimateMs(byte[] wav) {
        try {
            int byteRate = (wav[28] & 0xff) | ((wav[29] & 0xff) << 8) | ((wav[30] & 0xff) << 16) | ((wav[31] & 0xff) << 24);
            int dataLen = (wav[40] & 0xff) | ((wav[41] & 0xff) << 8) | ((wav[42] & 0xff) << 16) | ((wav[43] & 0xff) << 24);
            if (byteRate <= 0) {
                return 0;
            }
            return (int) (dataLen * 1000L / byteRate);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
