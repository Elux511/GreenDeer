package com.it.greendeer.tts;

import com.it.greendeer.config.AppProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * TTS 合成调度器：按音色名路由到对应 Provider；Provider 不可用/调用失败时
 * 自动降级到 mock(静音提示音)，保证互动链路不因语音服务中断。
 */
@Slf4j
@Service
public class TtsSynthesizer {

    private final AppProperties props;
    private final Map<String, TtsProvider> providers = new HashMap<>();
    private final MockTtsProvider mock;

    public TtsSynthesizer(AppProperties props, List<TtsProvider> providerList) {
        this.props = props;
        for (TtsProvider p : providerList) {
            providers.put(p.name(), p);
        }
        this.mock = (MockTtsProvider) providers.get("mock");
    }

    /** 音色信息（用于 /voices 与前端下拉）。 */
    public record VoiceInfo(String name, String provider, int sampleRate, String description, boolean available) {
    }

    /** 合成一段文本；voiceName 为空则用默认音色。永不抛异常（内部降级）。 */
    public TtsResult synthesize(String text, String voiceName) {
        String v = (voiceName == null || voiceName.isBlank()) ? props.getTts().getDefaultVoice() : voiceName.trim();
        VoiceInfo vi = findVoice(v);
        TtsProvider provider = providers.get(vi.provider());
        if (provider == null) {
            provider = mock;
        }
        try {
            return provider.synthesize(text, v, vi.sampleRate());
        } catch (Exception e) {
            log.warn("TTS({}) 合成失败，降级到 mock: {}", vi.provider(), e.getMessage());
            return mock.synthesize(text, "beep", vi.sampleRate());
        }
    }

    public List<VoiceInfo> listVoices() {
        List<VoiceInfo> out = new ArrayList<>();
        for (AppProperties.Tts.Voice v : effectiveVoices()) {
            TtsProvider p = providers.get(v.getProvider());
            boolean ok = p != null && p.available();
            out.add(new VoiceInfo(v.getName(), v.getProvider(), v.getSampleRate(), v.getDescription(), ok));
        }
        return out;
    }

    private VoiceInfo findVoice(String name) {
        AppProperties.Tts.Voice hit = null;
        for (AppProperties.Tts.Voice v : effectiveVoices()) {
            if (v.getName().equalsIgnoreCase(name)) {
                hit = v;
                break;
            }
        }
        if (hit != null) {
            return new VoiceInfo(hit.getName(), hit.getProvider(), hit.getSampleRate(), hit.getDescription(), true);
        }
        // 未在清单里：按名字推断 provider
        String low = name.toLowerCase(Locale.ROOT);
        String provider = low.startsWith("openai") ? "openai"
                : low.startsWith("sherpa") || low.startsWith("local") ? "sherpa-http" : props.getTts().getProvider();
        int sr = props.getTts().getVoices().isEmpty() ? 16000
                : props.getTts().getVoices().get(0).getSampleRate();
        return new VoiceInfo(name, provider, sr, "", true);
    }

    /** 若 yml 未配 voices，给出一组内置默认。 */
    private List<AppProperties.Tts.Voice> effectiveVoices() {
        if (!props.getTts().getVoices().isEmpty()) {
            return props.getTts().getVoices();
        }
        List<AppProperties.Tts.Voice> defaults = new ArrayList<>();
        defaults.add(voice("mock-silent", "mock", 16000, "静音占位音色(无引擎时演示)"));
        defaults.add(voice("sherpa-vits-zh-ll", "sherpa-http", 16000, "本地 sherpa-onnx VITS 中文"));
        defaults.add(voice("openai-alloy", "openai", 24000, "OpenAI 兼容 TTS alloy"));
        return defaults;
    }

    private AppProperties.Tts.Voice voice(String n, String p, int sr, String desc) {
        AppProperties.Tts.Voice v = new AppProperties.Tts.Voice();
        v.setName(n);
        v.setProvider(p);
        v.setSampleRate(sr);
        v.setDescription(desc);
        return v;
    }
}
