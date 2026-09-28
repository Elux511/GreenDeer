package com.it.greendeer.session;

import com.it.greendeer.chat.ChatCoordinator;
import com.it.greendeer.chat.InteractiveChatService;
import com.it.greendeer.common.BizException;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.message.ChatMessage;
import com.it.greendeer.domain.message.ChatMessageRepository;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.domain.session.LiveSessionRepository;
import com.it.greendeer.domain.session.PlatformType;
import com.it.greendeer.domain.session.SessionStatus;
import com.it.greendeer.live.LiveWsHub;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 直播场次服务：CRUD + 状态机(start/pause/stop) + 消息查询 + 广播配置。
 * 状态切换同步驱动 ChatCoordinator 与 WS 事件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    /** 场次视图。 */
    public record SessionView(Long id, String name, String status, String platform,
                              String rtmpUrl, String persona, String greeting, String ttsVoice,
                              boolean autoReply, String replyPrefix, Integer minReplyIntervalSec,
                              Integer maxHistory, Long messageCount, Integer queueSize, boolean busy,
                              Integer wsConnections, LocalDateTime startedAt, LocalDateTime endedAt,
                              LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    /** 场次创建/更新请求。 */
    public record UpsertReq(String name, String persona, String greeting, String platform, String rtmpUrl,
                            Boolean autoReply, String replyPrefix, String ttsVoice,
                            Integer minReplyIntervalSec, Integer maxHistory) {
    }

    private final LiveSessionRepository repository;
    private final ChatMessageRepository messageRepository;
    private final ChatCoordinator coordinator;
    private final InteractiveChatService chatService;
    private final LiveWsHub hub;
    private final AppProperties props;

    // ------------------------------------------------------------------ //

    @Transactional
    public SessionView create(UpsertReq req) {
        if (req.name() == null || req.name().isBlank()) {
            throw new BizException("场次名称不能为空");
        }
        LiveSession s = new LiveSession();
        s.setName(req.name().trim());
        s.setPersona(blankToNull(req.persona(), props.getChat().getDefaultPersona()));
        s.setGreeting(req.greeting());
        s.setPlatform(parsePlatform(req.platform()));
        s.setRtmpUrl(req.rtmpUrl());
        s.setAutoReply(req.autoReply() == null || req.autoReply());
        s.setReplyPrefix(req.replyPrefix());
        s.setTtsVoice(req.ttsVoice() == null || req.ttsVoice().isBlank() ? props.getTts().getDefaultVoice() : req.ttsVoice());
        s.setMinReplyIntervalSec(req.minReplyIntervalSec() == null ? props.getChat().getMinReplyIntervalSec() : req.minReplyIntervalSec());
        s.setMaxHistory(req.maxHistory() == null || req.maxHistory() <= 0 ? props.getChat().getMaxHistory() : req.maxHistory());
        return toView(repository.save(s));
    }

    public List<SessionView> list() {
        List<LiveSession> all = repository.findAll();
        List<SessionView> out = new ArrayList<>(all.size());
        for (LiveSession s : all) {
            out.add(toView(s));
        }
        return out;
    }

    public SessionView get(Long id) {
        return toView(require(id));
    }

    @Transactional
    public SessionView update(Long id, UpsertReq req) {
        LiveSession s = require(id);
        if (req.name() != null && !req.name().isBlank()) {
            s.setName(req.name().trim());
        }
        if (req.persona() != null) {
            s.setPersona(req.persona().isBlank() ? null : req.persona());
        }
        if (req.greeting() != null) {
            s.setGreeting(req.greeting().isBlank() ? null : req.greeting());
        }
        if (req.platform() != null) {
            s.setPlatform(parsePlatform(req.platform()));
        }
        if (req.rtmpUrl() != null) {
            s.setRtmpUrl(req.rtmpUrl().isBlank() ? null : req.rtmpUrl());
        }
        if (req.autoReply() != null) {
            s.setAutoReply(req.autoReply());
        }
        if (req.replyPrefix() != null) {
            s.setReplyPrefix(req.replyPrefix().isBlank() ? null : req.replyPrefix());
        }
        if (req.ttsVoice() != null) {
            s.setTtsVoice(req.ttsVoice().isBlank() ? props.getTts().getDefaultVoice() : req.ttsVoice());
        }
        if (req.minReplyIntervalSec() != null && req.minReplyIntervalSec() > 0) {
            s.setMinReplyIntervalSec(req.minReplyIntervalSec());
        }
        if (req.maxHistory() != null && req.maxHistory() > 0) {
            s.setMaxHistory(req.maxHistory());
        }
        return toView(repository.save(s));
    }

    /** 开始直播：CREATED/PAUSED → LIVE。 */
    @Transactional
    public SessionView start(Long id) {
        LiveSession s = require(id);
        if (s.getStatus() == SessionStatus.LIVE) {
            throw new BizException(409, "场次已在直播中");
        }
        if (s.getStatus() == SessionStatus.ENDED) {
            throw new BizException(409, "场次已结束，不能重新开始，请新建场次");
        }
        s.setStatus(SessionStatus.LIVE);
        if (s.getStartedAt() == null) {
            s.setStartedAt(LocalDateTime.now());
        }
        s.setEndedAt(null);
        repository.save(s);
        coordinator.activate(id);
        broadcast(s, "started", "直播已开始");
        return toView(s);
    }

    /** 暂停：LIVE → PAUSED（弹幕仍可入库但不触发 AI）。 */
    @Transactional
    public SessionView pause(Long id) {
        LiveSession s = require(id);
        if (s.getStatus() != SessionStatus.LIVE) {
            throw new BizException(409, "仅直播中可暂停");
        }
        s.setStatus(SessionStatus.PAUSED);
        repository.save(s);
        coordinator.deactivate(id);
        broadcast(s, "paused", "直播已暂停");
        return toView(s);
    }

    /** 结束：LIVE/PAUSED → ENDED。 */
    @Transactional
    public SessionView stop(Long id) {
        LiveSession s = require(id);
        if (s.getStatus() == SessionStatus.ENDED) {
            throw new BizException(409, "场次已结束");
        }
        if (s.getStatus() == SessionStatus.CREATED) {
            throw new BizException(409, "场次尚未开始");
        }
        s.setStatus(SessionStatus.ENDED);
        s.setEndedAt(LocalDateTime.now());
        repository.save(s);
        coordinator.deactivate(id);
        broadcast(s, "ended", "直播已结束");
        return toView(s);
    }

    /** 删除场次（仅 CREATED/ENDED 可删，删消息与音频目录由调用方扩展）。 */
    @Transactional
    public void delete(Long id) {
        LiveSession s = require(id);
        if (s.getStatus() == SessionStatus.LIVE || s.getStatus() == SessionStatus.PAUSED) {
            throw new BizException(409, "请先停止直播再删除");
        }
        messageRepository.deleteBySessionId(id);
        repository.delete(s);
    }

    /** 中断当前 AI 回复（转发给互动服务）。 */
    public void interrupt(Long id) {
        chatService.interrupt(id);
    }

    // ---------------- 消息查询 ---------------- //

    public record MessagePage(List<ChatMessage> items, boolean hasMore, long total) {
    }

    public MessagePage messages(Long id, Long afterId, int limit) {
        require(id);
        long after = afterId == null ? 0L : afterId;
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        List<ChatMessage> rows = messageRepository
                .findBySessionIdAndIdGreaterThanOrderByIdAsc(id, after, PageRequest.of(0, size + 1));
        boolean hasMore = rows.size() > size;
        List<ChatMessage> items = hasMore ? rows.subList(0, size) : rows;
        return new MessagePage(items, hasMore, messageRepository.countBySessionId(id));
    }

    /** 发送一条弹幕（触发 AI）。 */
    public ChatMessage comment(Long id, String userName, String content) {
        return chatService.handleInboundComment(id, userName, content);
    }

    // ---------------- 广播配置指引 ---------------- //

    /** 面向 OBS/浏览器采集的接线信息。 */
    public record BroadcastSetup(String consoleUrl, String digitalHumanPageUrl,
                                 String ebStreamUrl, String rtmpHint, String obsHint) {
    }

    public BroadcastSetup setup(Long id) {
        require(id);
        String consoleUrl = "/console.html?s=" + id;
        String ebUrl = "/api/v1/eb_stream";
        String dhPage = props.getDemo().getDigitalHumanPageUrl() == null ? "" : props.getDemo().getDigitalHumanPageUrl();
        String rtmpHint = "在 OBS 设置 → 直播 中把「服务」选为自定义，填入本场次配置的 RTMP 地址与串流密钥；"
                + "然后用 OBS 的「浏览器」来源采集数字人页面(" + dhPage + " 或本系统控制台)做绿幕合成，最后点击启动推流。";
        String obsHint = "如需后端遥控 OBS：OBS 菜单 工具→WebSocket 服务器设置 开启(默认端口 4455)，"
                + "然后在控制台 OBS 面板填写主机/端口/密码连接，即可一键启停推流与切场景。";
        return new BroadcastSetup(consoleUrl, dhPage, ebUrl, rtmpHint, obsHint);
    }

    // ------------------------------------------------------------------ //

    private LiveSession require(Long id) {
        return repository.findById(id).orElseThrow(() -> new BizException(404, "场次不存在: " + id));
    }

    private PlatformType parsePlatform(String platform) {
        if (platform == null || platform.isBlank()) {
            return PlatformType.CUSTOM;
        }
        try {
            return PlatformType.valueOf(platform.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BizException("platform 不合法: " + platform);
        }
    }

    private void broadcast(LiveSession s, String event, String message) {
        String json = "{\"type\":\"session_event\",\"sessionId\":" + s.getId()
                + ",\"event\":\"" + event + "\",\"message\":\"" + message + "\"}";
        hub.push(s.getId(), json);
    }

    private SessionView toView(LiveSession s) {
        boolean busy = s.getStatus() == SessionStatus.LIVE && coordinator.isBusy(s.getId());
        return new SessionView(s.getId(), s.getName(),
                s.getStatus() == null ? null : s.getStatus().name(),
                s.getPlatform() == null ? null : s.getPlatform().name(),
                s.getRtmpUrl(), s.getPersona(), s.getGreeting(), s.getTtsVoice(),
                s.isAutoReply(), s.getReplyPrefix(), s.getMinReplyIntervalSec(), s.getMaxHistory(),
                messageRepository.countBySessionId(s.getId()),
                coordinator.queueSize(s.getId()), busy,
                hub.subscriberCount(s.getId()),
                s.getStartedAt(), s.getEndedAt(), s.getCreatedAt(), s.getUpdatedAt());
    }

    private static String blankToNull(String v, String fallback) {
        if (v == null || v.isBlank()) {
            return fallback == null || fallback.isBlank() ? null : fallback;
        }
        return v;
    }
}
