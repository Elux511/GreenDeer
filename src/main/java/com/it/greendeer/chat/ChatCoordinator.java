package com.it.greendeer.chat;

import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.session.LiveSessionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 聊天协调器：场次 → 单 worker 串行循环 的注册表与生命周期管理。
 * 场次激活(LIVE)时创建 worker，结束/暂停时停用；弹幕入队若尚无 worker 会懒创建。
 */
@Slf4j
@Component
public class ChatCoordinator {

    private final ReplyEngine engine;
    private final LiveSessionRepository sessionRepository;
    private final WsReplySink wsSink;
    private final AppProperties props;
    private final Executor chatExecutor;
    private final ConcurrentHashMap<Long, ChatWorker> workers = new ConcurrentHashMap<>();
    private final AtomicInteger created = new AtomicInteger();

    public ChatCoordinator(ReplyEngine engine,
                           LiveSessionRepository sessionRepository,
                           WsReplySink wsSink,
                           AppProperties props,
                           @Qualifier("chatExecutor") Executor chatExecutor) {
        this.engine = engine;
        this.sessionRepository = sessionRepository;
        this.wsSink = wsSink;
        this.props = props;
        this.chatExecutor = chatExecutor;
    }

    /** 场次激活（开始直播）时调用。 */
    public void activate(Long sessionId) {
        getOrCreate(sessionId);
    }

    /** 场次停用（暂停/结束）时调用。 */
    public void deactivate(Long sessionId) {
        ChatWorker w = workers.remove(sessionId);
        if (w != null) {
            w.shutdown();
        }
    }

    /** 中断当前回复并清空队列。 */
    public void interrupt(Long sessionId) {
        ChatWorker w = workers.get(sessionId);
        if (w != null) {
            w.cancelCurrent();
        }
    }

    /** 入队一条弹幕回复任务；返回 false 表示队列已满被丢弃。 */
    public boolean enqueue(Long sessionId, CommentTask task) {
        ChatWorker w = getOrCreate(sessionId);
        return w.offer(task);
    }

    public int queueSize(Long sessionId) {
        ChatWorker w = workers.get(sessionId);
        return w == null ? 0 : w.queueSize();
    }

    public boolean isBusy(Long sessionId) {
        ChatWorker w = workers.get(sessionId);
        return w != null && w.isBusy();
    }

    private ChatWorker getOrCreate(Long sessionId) {
        return workers.computeIfAbsent(sessionId, id -> {
            ChatWorker w = new ChatWorker(id, props.getChat().getMaxQueue(), engine, sessionRepository, wsSink);
            chatExecutor.execute(w);
            created.incrementAndGet();
            log.info("创建 ChatWorker session={}", id);
            return w;
        });
    }
}
