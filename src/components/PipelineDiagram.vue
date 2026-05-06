<template>
  <section class="card">
    <h2>系统架构流水线（与立项图一致）</h2>
    <pre class="diagram" aria-label="architecture">{{ diagram }}</pre>
  </section>
</template>

<script setup lang="ts">
/** 分层与模块命名对齐立项架构图 */
const diagram = `┌─────────────────────────────────────────────────────────────输入层────────────┐
│ 弹幕采集模块    商品数据同步    观众统计模块                                      │
└───────────────────────────────┬───────────────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────处理层────────────┐
│ LLM 编排模块    意图识别模块    上下文管理    情绪分析模块                      │
│ （LLM：DeepSeek-V3 / Qwen-Max）  （ASR：阿里 Paraformer）                       │
└───────────────────────────────┬───────────────────────────────────────────────────┘
                                ▼
┌─────────────────────────────────────────────────────────────输出层────────────┐
│ TTS 语音合成模块 → Live2D 数字人驱动 → OBS 虚拟摄像头推流                       │
│ （TTS：CosyVoice / Edge）   （Cubism SDK）        ↑                             │
│                            商品卡片叠加 ──────────┘                             │
└───────────────────────────────┬───────────────────────────────────────────────────┘
                                ▼
                           平台观众端（延迟 < 3s 目标）

┌──────────────────────────────────────────────────────数据与基础设施层───────────┐
│ MySQL    Redis    Nginx    FFmpeg    阿里云 / 腾讯云                          │
│ （Redis：会话管理、弹幕队列）  （FFmpeg：媒体处理）                              │
└────────────────────────────────────────────────────────────────────────────────┘
                              ⇅ 与上三层双向支撑`
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
  font-size: 0.72rem;
  line-height: 1.4;
  color: var(--muted);
  background: #0c1017;
  border-radius: 10px;
  border: 1px solid var(--border);
  white-space: pre;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
}
</style>
