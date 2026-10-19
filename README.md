# 青鹿直播 · 前端（GreenDeer 后端配套 Web 控制台）

Vue 3 + TypeScript + Vite 的直播控制台与 OBS 叠加层，**直接对接 GreenDeer 后端**
（`/api/v1` REST + `/ws/live/{sessionId}` WebSocket）。接口契约以后端 `README.md` 为准。

---

## 1. 跑起来

```bash
npm install
cp .env.example .env      # Windows: copy .env.example .env
npm run dev               # http://127.0.0.1:5173
```

同时把后端跑起来（`8080`）：

```bash
cd ../CyanDeer-Live && ./mvnw spring-boot:run   # 或双击 start.bat
```

后端 `WebConfig` 已放开 CORS（`allowedOriginPatterns("*")`），前端 5173 直连 8080 无需代理。

环境变量：

| 变量 | 说明 |
|---|---|
| `VITE_API_BASE_URL` | 后端基址，默认 `http://127.0.0.1:8080` |
| `VITE_WS_BASE_URL` | 可选；留空则自动由 API 基址推导（http→ws） |

---

## 2. 页面对应的后端能力

| 页面 | 后端接口 |
|---|---|
| `/studio` 直播控制台 | `GET/POST /sessions`、`/sessions/{id}/start·pause·stop·interrupt`、`POST /sessions/{id}/comments`、`GET /sessions/{id}/messages`、`GET /sessions/{id}/setup` |
| 控制台 · 实时弹幕与 AI 回复 | `ws://host:8080/ws/live/{sessionId}`（帧：`comment` / `reply_start` / `reply_chunk` / `reply_error` / `session_event` / `obs_event` / `error`） |
| 控制台 · TTS 试音 | `GET /tts/voices`、`POST /tts/synthesize` |
| 控制台 · OBS 遥控 | `GET /obs/status`、`POST /obs/connect·disconnect`、`GET /obs/scenes`、`POST /obs/scenes/switch`、`GET /obs/stream/status`、`POST /obs/stream/start·stop` |
| `/overlay`、`/scene` | OBS 浏览器源叠加层（前端本地商品示例数据） |
| `/products` 商品与溯源 | 前端本地示例数据 |

> **商品库不在后端 MVP 内**（后端 README「8. 演进建议」已明确）。前端用本地 `MOCK_PRODUCTS`
> 渲染叠加层；后端补 `GET /api/v1/products` 后，把 `src/api/products.ts` 换成真实请求即可。

---

## 3. 典型联调流程

1. 后端启动（MySQL 就绪），浏览器打开本前端 `/studio`。
2. 「新建场次」→ 填名称/人设/音色 → 创建。
3. 点「开始」进入 `LIVE`；WS 自动连接，页面右侧出现系统事件。
4. 后端默认开启弹幕模拟器，会自动投喂弹幕 → 页面实时看到观众弹幕与 AI 流式回复（含音频）。
   也可用「发送弹幕」手动触发。
5. OBS 面板连接（4455）后可一键切场景/启停推流。
6. 把 `/overlay`、`/scene` 地址粘进 OBS 浏览器源做商品卡片叠加。

---

## 4. 目录

```
src/api/          后端接口封装（client 负责解包 {code,message,data}）
src/types/        接口数据类型（与后端 record 一一对应）
src/stores/       studio（场次+时间线）/ obs / products
src/composables/  useLiveSocket（/ws/live/{id} 帧适配）、obsLinks
src/views/        Home / Studio / Products / Overlay / Scene
streaming/        SRS / FFmpeg 推流示例脚本与说明
docs/             实施清单与后续事项
```

构建：`npm run build`（先 `vue-tsc --noEmit` 类型检查，再 `vite build`）。
