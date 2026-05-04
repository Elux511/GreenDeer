<template>
  <section class="card">
    <h2>交互流水线（概念）</h2>
    <pre class="diagram" aria-label="pipeline">{{ diagram }}</pre>
  </section>
</template>

<script setup lang="ts">
const diagram = `观众弹幕 / 语音
       │
       ▼
  采集模块 ──► ASR（如 Paraformer）
       │
       ▼
  意图识别 ◄──► 上下文管理 / 情绪分析
       │
       ▼
  LLM 编排（DeepSeek-V3 / Qwen-Max 等）
       │
       ├──────────────┐
       ▼              ▼
   TTS（CosyVoice    Live2D 口型驱动（Cubism）
    / Edge 等）            │
       │              └────┬────► OBS 虚拟摄像头 / FFmpeg
       └──────────────────┘              │
                                         ▼
                              平台观众端（低延迟 &lt; 3s 目标）`
</script>

<style scoped>
h2 {
  margin: 0 0 0.75rem;
  font-size: 1.1rem;
}

.diagram {
  margin: 0;
  padding: 1rem;
  overflow-x: auto;
  font-size: 0.82rem;
  line-height: 1.45;
  color: var(--muted);
  background: #0c1017;
  border-radius: 10px;
  border: 1px solid var(--border);
  white-space: pre;
}
</style>
