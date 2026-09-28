package com.it.greendeer.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.it.greendeer.chat.CommentTask;
import com.it.greendeer.chat.ReplyEngine;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.domain.session.LiveSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 兼容 DH_live 网页端对话协议：
 * POST /api/v1/eb_stream
 * body: {"input_mode":"text","prompt":"...","voice_id":"可选","voice_speed":"可选","sessionId":"可选"}
 * resp: NDJSON，每行 {"text":..,"audio":base64,"endpoint":bool}
 *
 * <p>把 DH_live 网页 demo（MiniLive.html / dialog.js）的 server_url 指向本端点，
 * 即可由本后端统一驱动 LLM/TTS 链路；传 sessionId 时会复用该场次人设与上下文。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class EbStreamController {

    private final ReplyEngine engine;
    private final LiveSessionRepository sessionRepository;
    private final AppProperties props;

    @PostMapping(value = "/api/v1/eb_stream", produces = MediaType.APPLICATION_JSON_VALUE)
    public StreamingResponseBody ebStream(@RequestBody(required = false) String rawBody) {
        // 说明：Boot4 MVC 默认 Jackson3(tools.jackson)，不能直接绑 com.fasterxml JsonNode，
        // 这里收原始串后用 Jackson2 工具解析，协议层零耦合。
        if (rawBody == null || rawBody.isBlank()) {
            throw new BizException("请求体不能为空");
        }
        JsonNode body;
        try {
            body = Json.parse(rawBody);
        } catch (IllegalArgumentException e) {
            throw new BizException(400, "JSON 解析失败: " + e.getMessage());
        }
        String inputMode = Json.textOrDefault(body, "input_mode", "text");
        String prompt = Json.text(body, "prompt");
        String voiceId = Json.text(body, "voice_id");
        Long sessionId = Json.longValue(body, "sessionId", 0L) == 0 ? null : Json.longValue(body, "sessionId", 0L);
        if (sessionId != null && sessionRepository.findById(sessionId).isEmpty()) {
            throw new BizException(404, "场次不存在: " + sessionId);
        }
        if ("audio".equalsIgnoreCase(inputMode)) {
            return out -> {
                NdjsonReplySink sink = new NdjsonReplySink(out);
                sink.sentence(0, 0, "当前未启用语音输入(ASR)，请使用文字弹幕互动～", true,
                        null, "audio/wav", 0, true);
            };
        }
        if (prompt == null || prompt.isBlank()) {
            throw new BizException("prompt 不能为空");
        }

        return out -> {
            LiveSession context = buildContext(sessionId, voiceId);
            CommentTask task = new CommentTask(null, "网页用户", prompt);
            NdjsonReplySink sink = new NdjsonReplySink(out);
            try {
                engine.runReply(context, task, sink, new AtomicBoolean(false), false);
            } catch (Exception e) {
                log.warn("eb_stream 处理失败: {}", e.getMessage());
                sink.error(0, 0, e.getMessage());
            }
        };
    }

    /** 组一个运行上下文：优先真实场次字段，voice_id 覆盖音色。 */
    private LiveSession buildContext(Long sessionId, String voiceId) {
        LiveSession base = sessionId == null ? null
                : sessionRepository.findById(sessionId).orElse(null);
        LiveSession ctx = new LiveSession();
        ctx.setId(sessionId); // 便于上下文查询(引擎按场次拉历史，不落库)
        String persona = base != null && base.getPersona() != null && !base.getPersona().isBlank()
                ? base.getPersona() : props.getChat().getDefaultPersona();
        ctx.setPersona(persona);
        ctx.setTtsVoice(voiceId == null || voiceId.isBlank()
                ? (base != null ? base.getTtsVoice() : props.getTts().getDefaultVoice()) : voiceId);
        ctx.setReplyPrefix("");
        ctx.setMaxHistory(base != null && base.getMaxHistory() != null ? base.getMaxHistory() : 0);
        return ctx;
    }
}
