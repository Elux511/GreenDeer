package com.it.greendeer.ai.llm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Mock 大模型：无 API Key 时用于演示闭环。
 * 从最近一条用户消息抽取话题，套用几个预设话术模板（多句、口语化）。
 */
@Slf4j
@Component
public class MockLlmProvider implements LlmProvider {

    private static final String[] TEMPLATES = {
            "关于「%s」这个问题呀，主播觉得可以从两个角度来看。一是要结合具体的使用场景，二呢要看大家各自的习惯，欢迎多聊聊～",
            "收到收到～「%s」这个我记在小本本上啦。不过答案可能会因情况而异，建议你多留意直播间里的实际演示哦。",
            "好问题！「%s」确实值得展开讲一讲。我先把要点整理一下，回头让主播给你细细说，别走开呀！"
    };

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public boolean available() {
        return true;
    }

    @Override
    public void streamReply(String systemPrompt, List<ChatTurn> history, AtomicBoolean cancel, Consumer<String> onDelta) {
        String lastUser = "";
        for (int i = history.size() - 1; i >= 0; i--) {
            ChatTurn t = history.get(i);
            if (ChatTurn.ROLE_USER.equals(t.role()) && !t.content().isBlank()) {
                lastUser = t.content();
                break;
            }
        }
        String topic = lastUser.trim();
        if (topic.length() > 18) {
            topic = topic.substring(0, 18) + "…";
        }
        String text = String.format(TEMPLATES[Math.floorMod(topic.hashCode(), TEMPLATES.length)], topic.isBlank() ? "今天的直播内容" : topic);
        // 以 4~8 字粒度模拟流式输出
        for (int i = 0; i < text.length(); i += 6) {
            if (cancel.get()) {
                return;
            }
            onDelta.accept(text.substring(i, Math.min(text.length(), i + 6)));
        }
        log.debug("[mock-llm] 回复: {}", text);
    }
}
