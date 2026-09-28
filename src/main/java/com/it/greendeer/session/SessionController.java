package com.it.greendeer.session;

import com.it.greendeer.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 直播场次 REST API。
 */
@RestController
@RequestMapping("/api/v1/sessions")
@RequiredArgsConstructor
public class SessionController {

    public record CommentReq(String userName, String content) {
    }

    private final SessionService service;

    @GetMapping
    public ApiResponse<List<SessionService.SessionView>> list() {
        return ApiResponse.ok(service.list());
    }

    @PostMapping
    public ApiResponse<SessionService.SessionView> create(@RequestBody SessionService.UpsertReq req) {
        return ApiResponse.ok(service.create(req));
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionService.SessionView> get(@PathVariable Long id) {
        return ApiResponse.ok(service.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<SessionService.SessionView> update(@PathVariable Long id,
                                                          @RequestBody SessionService.UpsertReq req) {
        return ApiResponse.ok(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/start")
    public ApiResponse<SessionService.SessionView> start(@PathVariable Long id) {
        return ApiResponse.ok(service.start(id));
    }

    @PostMapping("/{id}/pause")
    public ApiResponse<SessionService.SessionView> pause(@PathVariable Long id) {
        return ApiResponse.ok(service.pause(id));
    }

    @PostMapping("/{id}/stop")
    public ApiResponse<SessionService.SessionView> stop(@PathVariable Long id) {
        return ApiResponse.ok(service.stop(id));
    }

    @PostMapping("/{id}/interrupt")
    public ApiResponse<Void> interrupt(@PathVariable Long id) {
        service.interrupt(id);
        return ApiResponse.ok();
    }

    /** 游标拉取消息：afterId=上页最后一条 id，limit<=200。 */
    @GetMapping("/{id}/messages")
    public ApiResponse<SessionService.MessagePage> messages(@PathVariable Long id,
                                                            @RequestParam(required = false) Long afterId,
                                                            @RequestParam(required = false, defaultValue = "50") int limit) {
        return ApiResponse.ok(service.messages(id, afterId, limit));
    }

    /** 入站弹幕/评论（平台网关、控制台、模拟器统一入口），触发 AI 回复。 */
    @PostMapping("/{id}/comments")
    public ApiResponse<Object> comment(@PathVariable Long id, @RequestBody CommentReq req) {
        return ApiResponse.ok(service.comment(id, req.userName(), req.content()));
    }

    /** 直播接线指引（OBS / DH_live 页面 / eb_stream）。 */
    @GetMapping("/{id}/setup")
    public ApiResponse<SessionService.BroadcastSetup> setup(@PathVariable Long id) {
        return ApiResponse.ok(service.setup(id));
    }
}
