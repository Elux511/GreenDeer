package com.it.greendeer.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 应用配置（application.yml 的 app.* 段）。
 * 采用 @ConfigurationProperties 绑定，全部通过环境变量可覆盖。
 */
@Data
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** AI 回复音频落盘目录。 */
    private String audioDir = "./data/audio";

    private Llm llm = new Llm();
    private Tts tts = new Tts();
    private Chat chat = new Chat();
    private Obs obs = new Obs();
    private Demo demo = new Demo();

    // ------------------------------------------------------------------ //

    @Data
    public static class Llm {
        /** mock | openai */
        private String provider = "mock";
        /** 未配置场次人设时的默认系统提示。 */
        private String systemPrompt = "";
        private OpenAi openai = new OpenAi();

        @Data
        public static class OpenAi {
            private String baseUrl = "https://api.deepseek.com";
            private String apiKey = "";
            private String model = "deepseek-chat";
            private double temperature = 0.8;
            private int maxTokens = 512;
            private int timeoutSeconds = 90;
        }
    }

    @Data
    public static class Tts {
        /** mock | sherpa-http | openai */
        private String provider = "mock";
        private String defaultVoice = "mock-silent";
        private List<Voice> voices = new ArrayList<>();
        private Sherpa sherpa = new Sherpa();
        private OpenAi openai = new OpenAi();

        @Data
        public static class Voice {
            private String name;
            private String provider;
            private int sampleRate = 16000;
            private String description = "";
        }

        @Data
        public static class Sherpa {
            private String gatewayUrl = "http://127.0.0.1:8899";
            private int timeoutSeconds = 30;
        }

        @Data
        public static class OpenAi {
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey = "";
            private String model = "tts-1";
            private String voice = "alloy";
            private String responseFormat = "mp3";
        }
    }

    @Data
    public static class Chat {
        /** 同场次相邻 AI 回复的最小间隔(秒)。 */
        private int minReplyIntervalSec = 2;
        /** 送入 LLM 的最近对话条数上限。 */
        private int maxHistory = 12;
        /** 每场次待处理弹幕队列上限。 */
        private int maxQueue = 64;
        /** 单条弹幕最多字数。 */
        private int maxCommentLength = 200;
        /** 新建场次默认人设。 */
        private String defaultPersona = "";
        /** LLM/TTS 失败时的兜底回复。 */
        private String replyFallback = "";
    }

    @Data
    public static class Obs {
        private String host = "127.0.0.1";
        private int port = 4455;
        private String password = "";
        private int requestTimeoutSeconds = 6;
    }

    @Data
    public static class Demo {
        private boolean simulatorEnabled = true;
        private int simulatorIntervalSec = 6;
        private List<String> simulatorUserNames = new ArrayList<>();
        private List<String> simulatorComments = new ArrayList<>();
        /** 可选：DH_live 数字人页面地址，控制台内嵌预览、OBS Browser Source 采集用。 */
        private String digitalHumanPageUrl = "";
    }
}
