package com.it.greendeer.domain.session;

/**
 * 目标直播平台。MVP 阶段输出通用 RTMP 流(由 OBS 推送到对应平台)，
 * 枚举仅作标记，便于后续对接平台 API。
 */
public enum PlatformType {
    CUSTOM("通用/其他"),
    BILIBILI("哔哩哔哩"),
    DOUYIN("抖音"),
    KUAISHOU("快手"),
    WECHAT("微信视频号");

    private final String label;

    PlatformType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
