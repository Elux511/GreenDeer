<script setup lang="ts">
import PipelineDiagram from '@/components/PipelineDiagram.vue'
import ProductCardOverlay from '@/components/ProductCardOverlay.vue'
</script>

<template>
  <div class="stack">
    <section class="card hero">
      <h1>青鹿直播 · AI 数字人带货</h1>
      <p class="muted">
        本仓库承担 <strong>Vue 3 前端</strong>与<strong>直播推流相关模块</strong>（OBS / FFmpeg / SRS
        对接说明与示例）。后端微服务、LLM/TTS/ASR 等由独立服务提供，通过 WebSocket / HTTP 与本前端交互。
      </p>
    </section>

    <section class="card">
      <h2>端到端延迟目标</h2>
      <p class="muted">
        观众弹幕或语音 → 采集 / ASR → 意图识别 → LLM（约 1–2s）→ TTS（约 1–3s，可与上下文并行）→ 数字人口型驱动 →
        观众感知总延迟 &lt; 3s（需在链路与时序上持续优化）。
      </p>
    </section>

    <PipelineDiagram />

    <section class="card">
      <h2>商品卡片叠加预览（OBS 浏览器源可用）</h2>
      <p class="muted">下方为固定比例容器，模拟直播画面右下角叠加。</p>
      <div class="stage">
        <ProductCardOverlay
          name="番石榴（示例）"
          origin="潮州官塘"
          price="¥ 19.9 / 箱"
        />
      </div>
    </section>

    <section class="card grid2">
      <div>
        <h3>前端职责（本仓库）</h3>
        <ul class="muted">
          <li>直播控制台：弹幕视图、观众统计占位、一键开播状态</li>
          <li>商品卡片叠加层（价格、规格、链接）与产地信息展示占位</li>
          <li>Live2D / Cubism SDK 集成位（需按授权单独引入 SDK）</li>
          <li>低带宽适配：懒加载、资源压缩、首屏优化</li>
        </ul>
      </div>
      <div>
        <h3>推流职责（本仓库 streaming/）</h3>
        <ul class="muted">
          <li>OBS 虚拟摄像头 → RTMP/WebRTC 推至 SRS 的部署说明</li>
          <li>FFmpeg 转封装、多档位码率示例</li>
          <li>与云端（阿里云 / 腾讯云）推流域名、鉴权的环境变量模板</li>
        </ul>
      </div>
    </section>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 0.5rem;
  font-size: 1.6rem;
}

h2,
h3 {
  margin: 0 0 0.5rem;
  font-size: 1.1rem;
}

ul {
  margin: 0;
  padding-left: 1.2rem;
}

.hero strong {
  color: var(--accent);
}

.stage {
  position: relative;
  margin-top: 0.75rem;
  aspect-ratio: 16 / 9;
  border-radius: 12px;
  background:
    radial-gradient(1200px 400px at 20% 10%, rgba(94, 200, 255, 0.12), transparent 60%),
    linear-gradient(180deg, #0c1017, #0a0d12);
  border: 1px solid var(--border);
  overflow: hidden;
}
</style>
