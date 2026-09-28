# GreenDeer —— AI 数字人直播系统（后端）

围绕 [DH_live](https://github.com/kleinlee/DH_live)（2D 数字人/网页实时推理）构建的**直播业务后端**。
定位：DH_live 负责"把数字人画出来"，本后端负责"直播的大脑与编排"——
**场次管理 + 弹幕 AI 互动（LLM→TTS→实时推送）+ TTS 语音服务 + OBS 推流集成**。

直播画面链路：浏览器渲染数字人（DH_live WASM 页面）→ OBS 绿幕采集 → RTMP 推流到目标平台；
本系统为该链路提供 AI 互动内容与推流/调度控制，可与平台/网页端解耦。

> 技术栈：Java 17 · Spring Boot 4.1.1 · Spring WebMVC · Spring Data JPA · WebSocket · MySQL
> · OkHttp · Jackson（JSON）· 内置 Mock LLM/TTS 可零依赖演示

---

## 1. 模块总览

```
src/main/java/com/it/greendeer/
├── config/         AppProperties(application.yml 绑定) / AsyncConfig / WebConfig / WebSocketConfig
├── common/         ApiResponse / BizException / GlobalExceptionHandler / Json(Jackson 封装)
├── domain/
│   ├── session/    LiveSession 场次实体 · 状态机 CREATED→LIVE→PAUSED→ENDED · 仓储
│   └── message/    ChatMessage 弹幕/回复/系统流水 · 仓储(游标分页/上下文)
├── ai/
│   ├── llm/        LlmProvider 抽象 · MockLlmProvider · OpenAiLlmProvider(SSE 流式)
│   └── chat/       SentenceStream 流式分句器
├── chat/           ReplyEngine(LLM→分句→TTS→推送编排) · ChatWorker(单场次串行)
│                   ChatCoordinator · InteractiveChatService · AudioStore · WsReplySink
├── tts/            TtsProvider 抽象 · mock / sherpa-http / openai · TtsSynthesizer(自动降级)
├── live/           LiveWsHub(订阅中枢) · WebSocketLiveHandler(/ws/live/{id})
├── obs/            ObsClient(obs-websocket v5 JSON-RPC) · ObsService · ObsController
├── http/           EbStreamController(DH_live NDJSON 兼容) · NdjsonReplySink
└── simulator/      CommentSimulator(弹幕模拟器，演示闭环)
```

---

## 2. 快速开始

### 2.1 数据库

MySQL 连接默认 `127.0.0.1:3306/greendeer`（`createDatabaseIfNotExist=true`），
全部可环境变量覆盖（详见 `src/main/resources/application.yml`）：

```bash
# PowerShell 示例
$env:DB_HOST="127.0.0.1"; $env:DB_PORT="3306"; $env:DB_NAME="greendeer"
$env:DB_USER="root";      $env:DB_PASSWORD="你的密码"
```

表结构由 JPA `ddl-auto: update` 自动创建（`live_session`、`chat_message` 两表）。

### 2.2 启动

**方式 0（推荐 · 一键）**：**双击 `start.bat`**。首次运行时脚本会自动弹出向导，
按提示输入两次 MySQL 密码 → 自动生成 `local.env`（已 git 忽略、不会入库）→ 离线编译并启动
（约 1 分钟）；之后每次双击直接启动。Ctrl+C / 关窗口停止。

有网环境的常规方式：

```bash
mvnw spring-boot:run          # 或 mvn spring-boot:run
# 访问控制台: http://127.0.0.1:8080/console.html?s=<场次id>
```

**本机离线构建/验证**（本机 ~/.m2 没有 maven 生命周期插件 jar，用 javac 直编）：

```bash
pwsh -File tools/verify.ps1          # 离线 javac 编译 main+test 并跑 10 个单测
pwsh -File tools/run-offline.ps1     # 直接离线启动（首次自动编译；Ctrl+C 停止）
```

> 说明：本项目刻意不引入额外第三方依赖；JSON 统一走 Spring Boot 自带的 Jackson。

### 2.3 默认配置与切换

| 配置键 | 默认 | 说明 |
|---|---|---|
| `app.llm.provider` | `mock` | `mock`（无 Key 演示）/ `openai`（DeepSeek/豆包等 OpenAI 兼容，配 `LLM_API_KEY`） |
| `app.tts.provider` | `mock` | `mock`（静音+提示音 wav）/ `sherpa-http`（本地网关）/ `openai`（云端） |
| `app.tts.sherpa.gateway-url` | `http://127.0.0.1:8899` | 本地 sherpa-onnx 网关地址 |
| `app.obs.*` | `127.0.0.1:4455` | OBS WebSocket 服务器 |
| `app.demo.simulator-enabled` | `true` | 自动投喂模拟弹幕（演示闭环） |
| `app.demo.digital-human-page-url` | `` | DH_live 数字人页面地址（OBS Browser Source / 控制台内嵌） |

---

## 3. REST API（前缀 /api/v1）

### 直播场次 sessions
| 方法 | 路径 | 说明 |
|---|---|---|
| GET  | `/sessions` | 场次列表（含队列/忙碌/WS 连接数） |
| POST | `/sessions` | 创建 `{name, persona?, platform?, rtmpUrl?, autoReply?, replyPrefix?, ttsVoice?, ...}` |
| GET/PUT/DELETE | `/sessions/{id}` | 详情 / 更新 / 删除（须非 LIVE） |
| POST | `/sessions/{id}/start \| pause \| stop \| interrupt` | 状态机操作 / 中断当前 AI 回复 |
| GET  | `/sessions/{id}/messages?afterId=&limit=` | 弹幕+回复流水（游标分页） |
| POST | `/sessions/{id}/comments` | **入站弹幕** `{userName, content}`（平台网关/模拟器统一入口） |
| GET  | `/sessions/{id}/setup` | OBS / DH_live / eb_stream 接线指引 |

### TTS
| 方法 | 路径 | 说明 |
|---|---|---|
| GET  | `/tts/voices` | 音色列表（含 provider 与可用性） |
| POST | `/tts/synthesize` | `{text, voice?}` → `{provider,mime,sampleRate,durationMs,audioBase64}` |

### OBS（obs-websocket v5）
| 方法 | 路径 | 说明 |
|---|---|---|
| GET/POST | `/obs/status` `/obs/connect` `/obs/disconnect` | 连接管理（connect 可带 host/port/password） |
| GET/POST | `/obs/scenes` `/obs/scenes/switch` | 场景列表/切换 |
| GET/POST | `/obs/stream/status` `/obs/stream/start\|stop` | 推流控制 |

### WebSocket
`ws://<host>:8080/ws/live/{sessionId}` —— 下行帧：
`comment` / `reply_start` / `reply_chunk`(text+audio base64+endpoint) / `session_event` / `obs_event` / `error`。

### DH_live 兼容流式对话（NDJSON）
`POST /api/v1/eb_stream`，body `{input_mode:"text", prompt, voice_id?, sessionId?}`，
响应逐行 `{"text":"...","audio":"<base64 wav>","endpoint":true|false}`（与 DH_live dialog.js 协议一致）。

---

## 4. 与 DH_live 的三种接线方式

1. **浏览器+OBS 直播（推荐）**：DH_live 网页端渲染数字人，OBS 用「浏览器」来源采集并绿幕合成；
   后端只做 AI 互动（弹幕→人设 LLM→回复）→ 控制台把回复文本/音频送进直播间。
2. **eb_stream 驱动 DH_live 页面**：把 DH_live `web_demo/static/js/dialog.js` 顶部
   `server_url` 改为 `http://<后端>:8080/api/v1/eb_stream`，该页面所有对话改由本后端 LLM+TTS 驱动。
3. **OBS 遥控**：OBS 工具→WebSocket 服务器设置开启后，可在控制台一键切场景/启停推流。

详见 [docs/DH_live-INTEGRATION.md](docs/DH_live-INTEGRATION.md)。

---

## 5. 真人语音（TTS）接入

- **本地免费**：安装 `pip install sherpa-onnx soundfile`，下载 sherpa-onnx VITS 中文模型后：
  ```bash
  python tools/tts_gateway.py --models-dir <含 voice 目录的根> --port 8899
  ```
  后端把 `app.tts.provider` 设为 `sherpa-http`、`default-voice: sherpa-onnx-vits-zh-ll` 即可。
  （未装 sherpa 时网关 `/tts` 返回 501，后端会自动降级到 mock 提示音保证流程不断。）
- **云端**：`app.tts.provider=openai` + `TTS_API_KEY/TTS_BASE_URL`（OpenAI 兼容 /audio/speech）。
- **零依赖演示**：`app.tts.provider=mock` 输出"提示音+静音"的合法 wav，便于验证链路。

## 6. 弹幕 AI 互动流程

```
入站弹幕(POST /comments / WS / 模拟器)
  → 落库 COMMENT 并广播 comment 帧
  → 若场次 LIVE 且 autoReply：入 ChatWorker 串行队列
      → ReplyEngine：取人设 + 最近 N 条上下文(合并相邻同角色)
      → LLM 流式 → SentenceStream 分句 → 逐句 TTS → 推送 reply_start/reply_chunk(endpoint)
      → 落库 REPLY（全文+时长）；音频落盘 data/audio/{sessionId}/reply-*.wav
  异常：LLM/TTS 失败 → 兜底话术 / 降级 mock，回复链路不断流
```

## 7. 目录与文件

```
src/main/resources/application.yml    # 全部配置(环境变量可覆盖)
src/main/resources/static/console.html# 控制台(场次/弹幕/TTS试音/OBS/数字人接线)
tools/verify.ps1                      # 离线 javac+单测验证脚本
tools/tts_gateway.py                  # 本地 sherpa-onnx TTS 网关
tools/DbProbe.java                    # MySQL 连通探测(排障用)
docs/DH_live-INTEGRATION.md           # 与 DH_live 对接详解
db/init.sql                           # 可选：建库与最小账号示例
.mvn/offline-settings.xml             # 离线 mvn 专用 settings(去镜像)
```

## 8. 演进建议（不在本 MVP 内）

- 平台级：抖音/B站/视频号弹幕 WebSocket 网关（复用 `POST /sessions/{id}/comments` 即可）；
- 内容层：形象资产/剧本/商品库/知识库 RAG（场次已留 `persona/greeting/replyPrefix` 扩展点）；
- 工程化：切换 Flyway、加鉴权(登录/会话 token)、分布式部署时把 ChatWorker 换成消息队列。
