package com.it.greendeer.domain.message;

/**
 * 消息类型：
 * COMMENT 观众弹幕/评论（入站）；
 * REPLY   AI 回复（出站）；
 * SYSTEM  系统提示/事件记录。
 */
public enum MessageType {
    COMMENT,
    REPLY,
    SYSTEM
}
