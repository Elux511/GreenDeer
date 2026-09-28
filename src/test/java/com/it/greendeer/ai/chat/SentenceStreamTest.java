package com.it.greendeer.ai.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 流式分句器测试：句末标点后必须还有"非收尾字符"才切句；换行必切；残余靠 flush。
 */
class SentenceStreamTest {

    @Test
    void boundaryNeedsLookaheadChar() {
        SentenceStream s = new SentenceStream();
        // “。”后有“欢” → 立即切句
        assertEquals(List.of("你好呀。"), s.feed("你好呀。欢迎来到"));
        // “！”在流尾 → 不切，等后续
        assertTrue(s.feed("直播间！").isEmpty());
        // 后续字符到达 → 切出完整句，残余“继续聊”留在缓冲
        assertEquals(List.of("欢迎来到直播间！"), s.feed("继续聊"));
        // 没有新边界 → feed 返回空
        assertTrue(s.feed("哈哈").isEmpty());
        // 流结束 flush 残余
        assertEquals(List.of("继续聊哈哈"), s.flush());
    }

    @Test
    void punctuationAtStreamEndWaitsForFlush() {
        SentenceStream s = new SentenceStream();
        assertTrue(s.feed("只有一句。").isEmpty());
        assertEquals(List.of("只有一句。"), s.flush());
    }

    @Test
    void closingQuoteAfterPeriodDoesNotBreak() {
        SentenceStream s = new SentenceStream();
        assertTrue(s.feed("“真棒。").isEmpty());
        // 句号后紧跟收尾引号 → 不切；感叹号在流尾 → 不切
        assertTrue(s.feed("”不错！").isEmpty());
        // 感叹号后出现普通字符 → 从开头一直切到感叹号
        assertEquals(List.of("“真棒。”不错！"), s.feed("下一句吧。"));
        // 残余“下一句吧。”在流尾，靠 flush 兜底
        assertEquals(List.of("下一句吧。"), s.flush());
    }

    @Test
    void newlineAlwaysBreaks() {
        SentenceStream s = new SentenceStream();
        assertEquals(List.of("第一行\n"), s.feed("第一行\n第二"));
        // 剩余“第二”继续累积，无新边界 → feed 空
        assertTrue(s.feed("行结束").isEmpty());
        assertEquals(List.of("第二行结束"), s.flush());
    }

    @Test
    void emptyFeedKeepsState() {
        SentenceStream s = new SentenceStream();
        assertTrue(s.feed("").isEmpty());
        assertTrue(s.feed("abc").isEmpty());
        assertEquals(List.of("abc"), s.flush());
    }
}
