package com.it.greendeer.chat;

import com.it.greendeer.ai.llm.ChatTurn;
import com.it.greendeer.ai.llm.LlmProvider;
import com.it.greendeer.ai.llm.MockLlmProvider;
import com.it.greendeer.config.AppProperties;
import com.it.greendeer.domain.message.ChatMessage;
import com.it.greendeer.domain.message.ChatMessageRepository;
import com.it.greendeer.domain.message.MessageType;
import com.it.greendeer.domain.session.LiveSession;
import com.it.greendeer.tts.MockTtsProvider;
import com.it.greendeer.tts.TtsSynthesizer;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 回复引擎管线测试（无 Spring 上下文、无网络）：LLM 流式→分句→TTS→Sink + 落库。
 */
class ReplyEnginePipelineTest {

    private static final String LLM_TEXT = "这里是第一句。这里是第二句！";

    /** 记录输出的 Sink。 */
    static final class CollectSink implements ReplySink {
        record Chunk(long replyId, String text, boolean endpoint, byte[] audio, String mime, boolean first) {
        }

        final List<Chunk> chunks = new ArrayList<>();
        final List<String> errors = new ArrayList<>();

        @Override
        public void start(long sessionId, long replyId) {
        }

        @Override
        public void sentence(long sessionId, long replyId, String text, boolean endpoint,
                             byte[] audio, String mime, int durationMs, boolean first) {
            chunks.add(new Chunk(replyId, text, endpoint, audio, mime, first));
        }

        @Override
        public void error(long sessionId, long replyId, String message) {
            errors.add(message);
        }
    }

    /** 可控 LLM。 */
    static final class FakeLlm implements LlmProvider {
        final String text;
        final boolean throwOnReply;
        String system;
        List<ChatTurn> history;

        FakeLlm(String text, boolean throwOnReply) {
            this.text = text;
            this.throwOnReply = throwOnReply;
        }

        @Override
        public String name() {
            return "fake";
        }

        @Override
        public boolean available() {
            return true;
        }

        @Override
        public void streamReply(String systemPrompt, List<ChatTurn> history,
                                AtomicBoolean cancel, java.util.function.Consumer<String> onDelta) {
            this.system = systemPrompt;
            this.history = List.copyOf(history);
            if (throwOnReply) {
                throw new IllegalStateException("模拟 LLM 故障");
            }
            if (cancel != null && !cancel.get()) {
                onDelta.accept(text);
            }
        }
    }

    private AppProperties baseProps() {
        AppProperties p = new AppProperties();
        p.getChat().setDefaultPersona("默认人设");
        p.getChat().setMaxHistory(12);
        p.getChat().setReplyFallback("兜底话术：我这边开小差了，稍后再答你～");
        p.getLlm().setProvider("fake");
        p.getTts().setDefaultVoice("mock-silent");
        AppProperties.Tts.Voice v = new AppProperties.Tts.Voice();
        v.setName("mock-silent");
        v.setProvider("mock");
        v.setSampleRate(16000);
        p.getTts().setVoices(List.of(v));
        return p;
    }

    private ReplyEngine engine(AppProperties props, ChatMessageRepository repo) throws Exception {
        AudioStore store = new AudioStore(props);
        TtsSynthesizer synth = new TtsSynthesizer(props, List.of(new MockTtsProvider()));
        FakeLlm llm = new FakeLlm(LLM_TEXT, false);
        return new ReplyEngine(repo, props, synth, store, List.of(llm, new MockLlmProvider()));
    }

    @Test
    void fullPipelineProducesSentenceChunksAndPersistsReply() throws Exception {
        AppProperties props = baseProps();
        props.setAudioDir(Files.createTempDirectory("gd-test-audio").toString());
        ChatMessageRepository repo = mock(ChatMessageRepository.class);
        ReplyEngine engine = engine(props, repo);

        LiveSession session = new LiveSession();
        session.setId(1L);
        session.setPersona("你是测试主播：诙谐简短");
        CommentTask task = new CommentTask(99L, "观众甲", "给我讲个笑话吧");

        CollectSink sink = new CollectSink();
        long replyId = engine.runReply(session, task, sink, new AtomicBoolean(false), true);

        // 两句、末句 endpoint、首句带 first
        assertEquals(2, sink.chunks.size());
        assertEquals("这里是第一句。", sink.chunks.get(0).text());
        assertFalse(sink.chunks.get(0).endpoint());
        assertEquals("这里是第二句！", sink.chunks.get(1).text());
        assertTrue(sink.chunks.get(1).endpoint());
        assertTrue(sink.chunks.get(0).first());
        assertTrue(sink.chunks.get(0).audio().length > 44);
        assertEquals("audio/wav", sink.chunks.get(0).mime());
        assertEquals(replyId, sink.chunks.get(0).replyId());
        assertTrue(sink.errors.isEmpty());

        // 落库 REPLY：全文 = 两段文本拼接，replyToId = 99
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repo).save(captor.capture());
        ChatMessage saved = captor.getValue();
        assertEquals(MessageType.REPLY, saved.getType());
        assertEquals(LLM_TEXT, saved.getContent());
        assertEquals(99L, saved.getReplyToId());
        assertEquals(1L, saved.getSessionId());
    }

    @Test
    void llmFailureFallsBackAndPersistsFallback() throws Exception {
        AppProperties props = baseProps();
        props.setAudioDir(Files.createTempDirectory("gd-test-audio2").toString());
        ChatMessageRepository repo = mock(ChatMessageRepository.class);
        TtsSynthesizer synth = new TtsSynthesizer(props, List.of(new MockTtsProvider()));
        AudioStore store = new AudioStore(props);
        FakeLlm failing = new FakeLlm("", true);
        ReplyEngine engine = new ReplyEngine(repo, props, synth, store, List.of(failing, new MockLlmProvider()));

        LiveSession session = new LiveSession();
        session.setId(2L);
        CollectSink sink = new CollectSink();
        engine.runReply(session, new CommentTask(5L, "观众", "你好"), sink, new AtomicBoolean(false), true);

        assertFalse(sink.errors.isEmpty());
        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(repo).save(captor.capture());
        assertTrue(captor.getValue().getContent().contains("兜底话术"));
    }
}
