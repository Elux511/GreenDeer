package com.it.greendeer.chat;

/**
 * 一条待 AI 回复的弹幕任务。
 *
 * @param commentId 已落库的 COMMENT 消息 id
 * @param userName  弹幕用户名
 * @param content   弹幕内容
 */
public record CommentTask(Long commentId, String userName, String content) {
}
