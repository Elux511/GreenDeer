package com.it.greendeer.obs;

import com.it.greendeer.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * OBS 集成 REST API（通用 RTMP 推流场景）。统一返回 Map。
 */
@RestController
@RequestMapping("/api/v1/obs")
@RequiredArgsConstructor
public class ObsController {

    public record ConnectReq(String host, Integer port, String password) {
    }

    public record SceneReq(String sceneName) {
    }

    private final ObsService service;

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(service.status());
    }

    @PostMapping("/connect")
    public ApiResponse<Void> connect(@RequestBody(required = false) ConnectReq req) {
        service.connect(req == null ? null : req.host(),
                req == null ? null : req.port(),
                req == null ? null : req.password());
        return ApiResponse.ok();
    }

    @PostMapping("/disconnect")
    public ApiResponse<Void> disconnect() {
        service.disconnect();
        return ApiResponse.ok();
    }

    @GetMapping("/scenes")
    public ApiResponse<Map<String, Object>> scenes() {
        return ApiResponse.ok(service.scenes());
    }

    @PostMapping("/scenes/switch")
    public ApiResponse<Void> switchScene(@RequestBody SceneReq req) {
        service.switchScene(req.sceneName());
        return ApiResponse.ok();
    }

    @GetMapping("/stream/status")
    public ApiResponse<Map<String, Object>> streamStatus() {
        return ApiResponse.ok(service.streamStatus());
    }

    @PostMapping("/stream/start")
    public ApiResponse<Void> startStream() {
        service.startStream();
        return ApiResponse.ok();
    }

    @PostMapping("/stream/stop")
    public ApiResponse<Void> stopStream() {
        service.stopStream();
        return ApiResponse.ok();
    }
}
