package com.it.greendeer.domain.session;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 直播场次：一次直播的配置与生命周期记录。
 *
 * <p>直播画面由"浏览器渲染数字人 + OBS 绿幕采集推流"产出（对应 DH_live 网页端），
 * 本实体是后端编排的锚点：人设、目标平台/RTMP、AI 互动开关等。
 */
@Entity
@Table(name = "live_session", indexes = @Index(name = "idx_session_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
public class LiveSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 场次名称。 */
    @Column(nullable = false, length = 128)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SessionStatus status = SessionStatus.CREATED;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private PlatformType platform = PlatformType.CUSTOM;

    /** 目标 RTMP 推流地址(形如 rtmp://live.bilibili.com/live-stream/)。 */
    @Column(length = 512)
    private String rtmpUrl;

    /** 主播/助播人设（作为 LLM system prompt）。 */
    @Column(columnDefinition = "TEXT")
    private String persona;

    /** 开场白（保留字段，供后续自动化开场）。 */
    @Column(columnDefinition = "TEXT")
    private String greeting;

    /** 回复使用的默认音色名（对应 app.tts.voices）。 */
    @Column(length = 64)
    private String ttsVoice;

    /** 是否对弹幕自动 AI 回复。 */
    @Column(nullable = false)
    private boolean autoReply = true;

    /** 回复前缀模板，{name} 会被替换为弹幕用户名，例如 "@{name} "。 */
    @Column(length = 64)
    private String replyPrefix;

    /** 同场次 AI 回复的最小间隔(秒)，防止弹幕风暴打爆 LLM。 */
    private Integer minReplyIntervalSec = 2;

    /** 该场次送入 LLM 的上下文条数上限。 */
    private Integer maxHistory = 12;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
