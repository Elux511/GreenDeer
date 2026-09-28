package com.it.greendeer.chat;

import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.domain.session.LiveSessionRepository;
import com.it.greendeer.domain.session.SessionStatus;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单场次的串行弹幕处理循环：同一场次的 AI 回复永远只有一个在执行，
 * 避免多线程抢答导致上下文错乱。
 */
@Slf4j
public class ChatWorker implements Runnable {

    private final Long sessionId;
    private final ReplyEngine engine;
    private final LiveSessionRepository sessionRepository;
    private final ReplySink sink;

    private final LinkedBlockingQueue<CommentTask> queue;
    private final AtomicBoolean active = new AtomicBoolean(true);
    private final AtomicBoolean cancel = new AtomicBoolean(false);
    private volatile boolean busy = false;

    public ChatWorker(Long sessionId, int capacity, ReplyEngine engine,
                      LiveSessionRepository sessionRepository, ReplySink sink) {
        this.sessionId = sessionId;
        this.engine = engine;
        this.sessionRepository = sessionRepository;
        this.sink = sink;
        this.queue = new LinkedBlockingQueue<>(Math.max(8, capacity));
    }

    /** 入队弹幕任务；队列满返回 false。 */
    public boolean offer(CommentTask task) {
        if (!active.get()) {
            return false;
        }
        return queue.offer(task);
    }

    /** 清空队列并取消当前回复。 */
    public void cancelCurrent() {
        queue.clear();
        cancel.set(true);
    }

    /** 停用：结束循环并释放。 */
    public void shutdown() {
        active.set(false);
        cancel.set(true);
        queue.clear();
    }

    public boolean isBusy() {
        return busy;
    }

    public int queueSize() {
        return queue.size();
    }

    public boolean isActive() {
        return active.get();
    }

    @Override
    public void run() {
        log.info("ChatWorker 启动 session={}", sessionId);
        try {
            while (active.get()) {
                CommentTask task;
                try {
                    task = queue.poll(500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                if (task == null) {
                    continue;
                }
                if (!active.get()) {
                    break;
                }
                busy = true;
                cancel.set(false);
                try {
                    LiveSession session = sessionRepository.findById(sessionId).orElse(null);
                    if (session == null) {
                        log.warn("场次不存在 session={}，停止 worker", sessionId);
                        break;
                    }
                    if (session.getStatus() == SessionStatus.LIVE) {
                        engine.runReply(session, task, sink, cancel, true);
                    } else {
                        log.debug("场次非 LIVE，跳过回复 session={} status={}", sessionId, session.getStatus());
                    }
                } catch (Exception e) {
                    log.error("回复处理异常 session={}", sessionId, e);
                } finally {
                    busy = false;
                }
            }
        } finally {
            log.info("ChatWorker 退出 session={}", sessionId);
        }
    }
}
