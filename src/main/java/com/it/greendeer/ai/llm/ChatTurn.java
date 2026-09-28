package com.it.greendeer.ai.llm;

/**
 * 对话轮次，与 OpenAI Chat 格式对齐。
 *
 * @param role    system | user | assistant
 * @param content 内容
 */
public record ChatTurn(String role, String content) {

    public static final String ROLE_SYSTEM = "system";
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";

    public static ChatTurn system(String content) {
        return new ChatTurn(ROLE_SYSTEM, content);
    }

    public static ChatTurn user(String content) {
        return new ChatTurn(ROLE_USER, content);
    }

    public static ChatTurn assistant(String content) {
        return new ChatTurn(ROLE_ASSISTANT, content);
    }
}
