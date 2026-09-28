package com.it.greendeer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GreenDeer —— AI 数字人直播系统后端。
 *
 * <p>整体定位：把 DH_live 数字人能力当作"输出设备/渲染端"，本后端承担直播业务的大脑与编排：
 * 场次管理、弹幕 AI 互动(LLM→TTS→实时推送)、TTS 语音服务、OBS 推流集成。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class GreenDeerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GreenDeerApplication.class, args);
    }

}
