package com.it.greendeer.simulator;

import com.it.greendeer.chat.InteractiveChatService;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.domain.session.LiveSessionRepository;
import com.it.greendeer.domain.session.SessionStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 弹幕模拟器：向 LIVE 场次周期性投喂预设弹幕，无需平台即可闭环演示
 * "弹幕 → LLM → TTS → 数字人页面"全链路。app.demo.simulator-enabled 可关闭。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CommentSimulator {

    private final LiveSessionRepository sessionRepository;
    private final InteractiveChatService chatService;
    private final AppProperties props;

    private final SecureRandom random = new SecureRandom();
    private final Map<Long, Long> nextFireAt = new ConcurrentHashMap<>();

    @Scheduled(fixedDelay = 2000)
    public void tick() {
        if (!props.getDemo().isSimulatorEnabled()) {
            return;
        }
        List<LiveSession> live = sessionRepository.findByStatusIn(List.of(SessionStatus.LIVE));
        if (live.isEmpty()) {
            nextFireAt.clear();
            return;
        }
        long now = System.currentTimeMillis();
        int intervalMs = Math.max(2000, props.getDemo().getSimulatorIntervalSec() * 1000);
        List<String> users = props.getDemo().getSimulatorUserNames();
        List<String> comments = props.getDemo().getSimulatorComments();
        if (users.isEmpty() || comments.isEmpty()) {
            return;
        }
        for (LiveSession s : live) {
            long next = nextFireAt.getOrDefault(s.getId(), 0L);
            if (now < next) {
                continue;
            }
            try {
                String user = users.get(random.nextInt(users.size()));
                String content = comments.get(random.nextInt(comments.size()));
                chatService.handleInboundComment(s.getId(), user, content);
            } catch (Exception e) {
                log.debug("模拟弹幕失败 session={}: {}", s.getId(), e.getMessage());
            }
            nextFireAt.put(s.getId(), now + intervalMs + random.nextInt(intervalMs));
        }
    }
}
