package com.it.greendeer.domain.message;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 直播间消息流水：观众弹幕 + AI 回复 + 系统事件，全部落库可回放。
 */
@Entity
@Table(name = "chat_message", indexes = @Index(name = "idx_msg_session", columnList = "sessionId,id"))
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MessageType type;

    /** 弹幕用户名（AI 回复时可为空或 AI 昵称）。 */
    @Column(length = 64)
    private String userName;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    /** 回复音频相对路径(落盘于 app.audio-dir 下)，便于回放。 */
    @Column(length = 255)
    private String audioPath;

    /** 回复音频时长(ms)。 */
    private Integer audioDurationMs;

    /** 本条回复回应的观众弹幕 id（关联字段，非外键约束）。 */
    private Long replyToId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
