package com.it.greendeer.ai.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式分句器：把 LLM 增量文本切成"完整句子"，供逐句 TTS + 推送。
 *
 * <p>规则：遇到句末标点 [。！？!?；;\n] 且其后还有"一个非收尾字符"(保证不是标点串中间)
 * 即认为句子完整并切出；流结束时 flush() 兜底输出剩余文本。
 */
public class SentenceStream {

    private static final String BOUNDARY = "。！？!?；;\n";
    /** 紧随句末标点之后、仍不算断句的收尾字符（引号括号省略号等）。 */
    private static final String CLOSING = "”’』」）〉》…~～、，,.";

    private final StringBuilder buf = new StringBuilder();
    private final List<String> emitted = new ArrayList<>();

    /** 输入一段增量文本，返回本次完整切出的句子（可能为空）。 */
    public List<String> feed(String chunk) {
        buf.append(chunk);
        emitted.clear();
        int pos;
        while ((pos = findBoundary(buf)) >= 0) {
            emitted.add(buf.substring(0, pos + 1));
            buf.delete(0, pos + 1);
        }
        return emitted;
    }

    /** 流结束：把残余文本作为最后一句输出（过短则并入已输出或丢弃）。 */
    public List<String> flush() {
        emitted.clear();
        String rest = buf.toString();
        buf.setLength(0);
        if (!rest.isEmpty()) {
            emitted.add(rest);
        }
        return emitted;
    }

    /** 找到第一个"安全的句子结尾"下标（含标点），没有则返回 -1。 */
    private int findBoundary(StringBuilder sb) {
        int n = sb.length();
        for (int i = 0; i < n; i++) {
            char c = sb.charAt(i);
            if (BOUNDARY.indexOf(c) < 0) {
                continue;
            }
            if (c == '\n') {
                return i; // 换行即断句
            }
            // 句末标点后至少还有一个字符，且不是收尾字符，才认为句子结束
            if (i + 1 < n && CLOSING.indexOf(sb.charAt(i + 1)) < 0) {
                return i;
            }
        }
        return -1;
    }
}
