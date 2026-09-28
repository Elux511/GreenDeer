# 与 DH_live 对接详解

本文说明 GreenDeer 后端如何围绕 [kleinlee/DH_live](https://github.com/kleinlee/DH_live) 组成一套可运营的
AI 数字人直播系统。前提假设：你已按 DH_live README 把网页资源、模型与形象 assets 准备妥当
（`web_demo/` 目录 + `python web_demo/server_realtime.py` 或 `server.py`）。

## 1. 两种"数字人画面"模式

| 模式 | 画面来源 | 适用 |
|---|---|---|
| A. 浏览器 + OBS（推荐） | OBS「浏览器」来源采集 DH_live 页面（绿幕/前后景分离由 DH_live mini2.0 处理），数字人说话/口型在页面内完成 | 有真人运营或半自动直播 |
| B. 纯后端无人值守 | 后端合成音频→DH_live 离线视频合成→ffmpeg/轮播推流 | 无人直播/录播循环（需 Windows+GPU） |

本后端为模式 A 提供完整编排；模式 B 需要的场次/RTMP/剧本字段已预留（`rtmpUrl` 等）。

## 2. 部署拓扑（模式 A）

```
观众评论 ──(平台弹幕网关, 后续接入)──▶ POST /api/v1/sessions/{id}/comments
浏览器数字人页(DH_live MiniLive) ◀──WASM 推理、WebCodecs 音频驱动口型
        │ 采集
        ▼
OBS(Browser Source + 绿幕) ──RTMP──▶ 平台(抖音/B站/视频号/自建 SRS)
        ▲ 遥控(obs-websocket 4455)
        └──────────────── GreenDeer(8080)：场次 · AI 互动 · TTS · eb_stream
```

## 3. 三步接线

1. **启动数字人页面**：按 DH_live README 准备 assets 后运行
   ```bash
   python web_demo/server.py        # 或 server_realtime.py
   ```
   打开 `http://127.0.0.1:8888/static/MiniLive.html` 验证页面可用（WebCodecs 需要 localhost 或 HTTPS）。

2. **把对话服务指向 GreenDeer**：编辑 `DH_live/web_demo/static/js/dialog.js` 首行
   ```js
   let server_url = "http://127.0.0.1:8080/api/v1/eb_stream"
   ```
   这样 DH_live 页面每次对话改为请求本后端：LLM 用场次人设（可选传 `sessionId`）、TTS 逐句返回。
   `server.py` 原 /eb_stream 逻辑即被本后端替代（本后端协议完全兼容：每行 `{text,audio,endpoint}`）。

3. **OBS 采集与推流**：
   - 在 OBS 添加「浏览器」来源，URL 填数字人页地址（或本系统 `/console.html` 的 DH_live 预览 iframe 页）；
   - 开启 OBS 的 WebSocket 服务器（工具→WebSocket 服务器设置，默认 4455），
     在 GreenDeer 控制台 OBS 面板连接后即可一键：切换场景 / 开始-停止推流；
   - RTMP 目标：平台开播页复制 rtmp 地址与串流密钥，填进场次 `rtmpUrl` 与 OBS 设置→直播。

## 4. 弹幕源接入约定

平台/自制网关把"实时评论"转发给：

```
POST /api/v1/sessions/{id}/comments
{ "userName": "观众昵称", "content": "弹幕文本" }
```

后端自动：落库 → WS 广播 → AI 回复（逐句 text+audio）→ 消息流水可回放。
当前 MVP 自带 `CommentSimulator`（启动场次后自动投喂弹幕）用于无平台闭环演示；
在 `application.yml` 把 `app.demo.simulator-enabled` 置 `false` 即可关闭。

## 5. 关键坑位清单

- **WebCodecs**：数字人页面仅在 localhost/HTTPS 可用；局域网部署请上 HTTPS 或反代。
- **TTS 引擎**：DH_live 默认 TTS(sherpa-onnx VITS) 是本地 CPU 模型；GreenDeer 的
  `tools/tts_gateway.py` 直接复用该模型目录（`web_demo/models/...` 或任意含 `model.onnx` 的目录）。
- **音频采样率**：不同 VITS 模型输出 16k/22.05k/44.1k 不等；页面播放无影响，落库仅作回放。
- **eb_stream 无状态**：不带 `sessionId` 时无上下文；要做"直播间连续人设对话"请走
  `/sessions/{id}/comments` + WS（有历史上下文、落库、可中断）。
- **不要双写消息**：同一场次请只选一条驱动链路（eb_stream 或 comments+WS），避免消息重复入历史。
