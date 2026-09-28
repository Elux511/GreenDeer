package com.it.greendeer.tts;

import com.it.greendeer.common.ApiResponse;
import com.it.greendeer.common.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.List;

/**
 * TTS 语音生成服务 REST API。
 */
@RestController
@RequestMapping("/api/v1/tts")
@RequiredArgsConstructor
public class TtsController {

    public record SynthReq(String text, String voice) {
    }

    public record SynthResp(String provider, String mime, int sampleRate, int durationMs, String audioBase64) {
    }

    private final TtsSynthesizer synthesizer;

    @GetMapping("/voices")
    public ApiResponse<List<TtsSynthesizer.VoiceInfo>> voices() {
        return ApiResponse.ok(synthesizer.listVoices());
    }

    @PostMapping("/synthesize")
    public ApiResponse<SynthResp> synthesize(@RequestBody SynthReq req) {
        if (req.text() == null || req.text().isBlank()) {
            throw new BizException("text 不能为空");
        }
        if (req.text().length() > 1000) {
            throw new BizException("text 过长（≤1000 字）");
        }
        TtsResult r = synthesizer.synthesize(req.text(), req.voice());
        return ApiResponse.ok(new SynthResp(r.provider(), r.mime(), r.sampleRate(), r.durationMs(),
                Base64.getEncoder().encodeToString(r.audio())));
    }
}
