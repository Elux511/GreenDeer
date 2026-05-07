<script setup lang="ts">
import PipelineDiagram from '@/components/PipelineDiagram.vue'
import ProductCardOverlay from '@/components/ProductCardOverlay.vue'
</script>

<template>
  <div class="stack">
    <section class="card hero">
      <h1>青鹿直播 · AI 数字人带货</h1>
      <p class="muted">
        架构分层：<strong>输入层</strong>（弹幕采集、商品数据同步、观众统计）→
        <strong>处理层</strong>（LLM 编排、意图识别、上下文管理、情绪分析）→
        <strong>输出层</strong>（TTS、Live2D 数字人驱动、<strong>OBS 虚拟摄像头推流</strong>、商品卡片叠加），由
        <strong>数据与基础设施层</strong>（MySQL、Redis、Nginx、FFmpeg、阿里云/腾讯云）支撑。本仓库覆盖 Vue 前端（含<strong>商品卡片叠加</strong>）与推流说明（<strong>OBS + FFmpeg</strong> 选型口径）。
      </p>
    </section>

    <section class="card">
      <h2>端到端延迟目标</h2>
      <p class="muted">
        观众弹幕或语音 → 采集 / ASR（Paraformer）→ 意图识别 → LLM（约 1–2s）→ TTS（约 1–3s，可与上下文并行）→
        Live2D 口型驱动 → <strong>OBS 虚拟摄像头推流</strong> → 观众感知总延迟 &lt; 3s。
      </p>
    </section>

    <PipelineDiagram />

    <section class="card">
      <h2>商品卡片叠加（输出层 · OBS 浏览器源）</h2>
      <p class="muted">下方为 16:9 容器，模拟输出层中与 Live2D 同屏的叠加效果。</p>
      <div class="stage">
        <ProductCardOverlay
          name="番石榴（示例）"
          origin="潮州官塘"
          price="¥ 19.9 / 箱"
          link-url="https://example.com/product/guava-demo"
        />
      </div>
    </section>

    <section class="card grid2">
      <div>
        <h3>本仓库 · 前端</h3>
        <ul class="muted">
          <li>输入层展示占位：弹幕、观众统计、商品同步（对接 Spring Boot）</li>
          <li>输出层：<strong>商品卡片叠加</strong>、Live2D / Cubism 接入位</li>
          <li>弱网：懒加载、资源压缩、首屏优化</li>
        </ul>
      </div>
      <div>
        <h3>本仓库 · streaming/</h3>
        <ul class="muted">
          <li>输出层：<strong>OBS 虚拟摄像头推流</strong>操作说明（农户 → 直播伴侣）</li>
          <li>基础设施层：<strong>FFmpeg</strong> 脚本示例（媒体处理、调试）</li>
        </ul>
      </div>
    </section>

    <section class="card">
      <h2>OBS 浏览器源 URL（已实现）</h2>
      <ul class="muted">
        <li><code>/overlay?productId=…</code>：仅<strong>商品卡片叠加</strong>，透明背景（源设置勾选透明）</li>
        <li><code>/scene?productId=…</code>：<strong>数字人占位区 + 商品卡片</strong>，单浏览器源合成</li>
        <li>在<strong>直播控制台</strong>选商品后一键复制上述地址</li>
      </ul>
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
