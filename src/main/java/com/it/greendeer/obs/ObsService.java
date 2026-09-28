package com.it.greendeer.obs;

import com.fasterxml.jackson.databind.JsonNode;
import com.it.greendeer.common.BizException;
import com.it.greendeer.common.Json;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.live.LiveWsHub;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * OBS 控制服务：管理单实例连接、场景切换、推流启停、状态查询；
 * 把 OBS 事件以 {"type":"obs_event",...} 广播到所有控制台。
 * REST 统一返回 Map（Jackson3 MVC 序列化兼容）。
 */
@Slf4j
@Service
public class ObsService {

    private final ObsClient client = new ObsClient();
    private final AppProperties props;
    private final LiveWsHub hub;

    public ObsService(AppProperties props, LiveWsHub hub) {
        this.props = props;
        this.hub = hub;
        client.addEventListener(event -> {
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "obs_event");
            frame.put("eventType", Json.text(event, "eventType"));
            JsonNode data = event.get("eventData");
            frame.put("eventData", data == null ? new LinkedHashMap<>() : Json.toPlain(data));
            hub.broadcastAll(Json.write(Json.toNode(frame)));
        });
    }

    /** 连接 OBS（参数缺省时用配置文件）。 */
    public void connect(String host, Integer port, String password) {
        String h = (host == null || host.isBlank()) ? props.getObs().getHost() : host;
        int p = port == null ? props.getObs().getPort() : port;
        String pass = password == null ? props.getObs().getPassword() : password;
        try {
            client.connect(h, p, pass, props.getObs().getRequestTimeoutSeconds());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(400, "连接失败: " + e.getMessage());
        }
    }

    public void disconnect() {
        client.disconnect();
    }

    public Map<String, Object> status() {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("connected", client.isConnected());
        r.put("defaultHost", props.getObs().getHost());
        r.put("defaultPort", props.getObs().getPort());
        if (client.isConnected()) {
            try {
                r.put("obsVersion", Json.toPlain(client.request("GetVersion", null, props.getObs().getRequestTimeoutSeconds())));
            } catch (Exception ignore) {
            }
            try {
                r.put("streamStatus", Json.toPlain(client.request("GetStreamStatus", null, props.getObs().getRequestTimeoutSeconds())));
            } catch (Exception ignore) {
            }
        }
        return r;
    }

    /** 场景列表：{current, scenes:[...]}。 */
    public Map<String, Object> scenes() {
        JsonNode data = client.request("GetSceneList", null, props.getObs().getRequestTimeoutSeconds());
        return data == null ? new LinkedHashMap<>() : (Map<String, Object>) Json.toPlain(data);
    }

    public void switchScene(String sceneName) {
        if (sceneName == null || sceneName.isBlank()) {
            throw new BizException("sceneName 不能为空");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sceneName", sceneName);
        client.request("SetCurrentProgramScene", Json.toNode(data), props.getObs().getRequestTimeoutSeconds());
    }

    public Map<String, Object> streamStatus() {
        JsonNode n = client.request("GetStreamStatus", null, props.getObs().getRequestTimeoutSeconds());
        return n == null ? new LinkedHashMap<>() : (Map<String, Object>) Json.toPlain(n);
    }

    public void startStream() {
        client.request("StartStream", null, props.getObs().getRequestTimeoutSeconds());
    }

    public void stopStream() {
        client.request("StopStream", null, props.getObs().getRequestTimeoutSeconds());
    }

    @PreDestroy
    public void close() {
        client.disconnect();
    }
}
