<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import PipelineDiagram from '@/components/PipelineDiagram.vue'
import ProductCardOverlay from '@/components/ProductCardOverlay.vue'
import { apiBaseUrl, liveSocketUrl } from '@/config'
import { errorText, isApiConfigured } from '@/api/client'
import { listSessions } from '@/api/sessions'

const configured = isApiConfigured()
const apiBase = apiBaseUrl()
const wsSample = liveSocketUrl('{sessionId}')

const online = ref<boolean | null>(null)
const sessionCount = ref(0)
const message = ref('')

const healthText = computed(() => {
  if (!configured) return '未配置后端地址'
  if (online.value === null) return '检测中…'
  return online.value ? `后端在线 · 共 ${sessionCount.value} 个场次` : `后端不可达：${message.value}`
})

onMounted(async () => {
  if (!configured) return
  try {
    const list = await listSessions()
    sessionCount.value = list.length
    online.value = true
  } catch (e) {
    online.value = false
    message.value = errorText(e)
  }
})
</script>

<template>
  <div class="stack">
    <section class="card hero">
      <h1>青鹿直播 · AI 数字人带货</h1>
      <p class="muted">
        架构分层：<strong>输入层</strong>（弹幕采集、商品数据、观众统计）→
        <strong>处理层</strong>（LLM 编排、意图识别、上下文管理）→
        <strong>输出层</strong>（TTS 语音、数字人驱动、<strong>OBS 推流</strong>、商品卡片叠加）。
        后端 GreenDeer 负责场次编排与 AI 互动，本前端负责运营控制台与 OBS 叠加层。
      </p>
      <div class="status">
        <span class="muted">后端</span>
        <code v-if="apiBase">{{ apiBase }}</code>
        <span v-else class="warn">未配置 VITE_API_BASE_URL</span>
        <span :class="['badge', online === true ? 'ok' : online === false ? 'bad' : '']">{{ healthText }}</span>
        <RouterLink to="/studio" class="btn primary">进入直播控制台</RouterLink>
      </div>
    </section>

    <section class="card">
      <h2>端到端延迟目标</h2>
      <p class="muted">
        观众弹幕 → 采集 → LLM（约 1–2s）→ TTS（约 1–3s，可与上下文并行）→ 数字人口型驱动 →
        OBS 虚拟摄像头推流 → 观众感知总延迟 &lt; 3s。
      </p>
    </section>

    <PipelineDiagram />

    <section class="card">
      <h2>它和真实后端怎么连</h2>
      <div class="rows">
        <div class="row">
          <span class="muted">场次与状态机</span>
          <span><code>GET/POST /api/v1/sessions</code>、<code>/sessions/{id}/start·pause·stop</code></span>
        </div>
        <div class="row">
          <span class="muted">实时弹幕 / AI 回复</span>
          <span><code>{{ wsSample }}</code></span>
        </div>
        <div class="row">
          <span class="muted">入站弹幕</span>
          <span><code>POST /api/v1/sessions/{id}/comments</code></span>
        </div>
        <div class="row">
          <span class="muted">语音合成</span>
          <span><code>/api/v1/tts/voices</code>、<code>/tts/synthesize</code></span>
        </div>
        <div class="row">
          <span class="muted">OBS 遥控</span>
          <span><code>/api/v1/obs/status·connect·scenes·stream</code></span>
        </div>
      </div>
    </section>

    <section class="card">
      <h2>商品卡片叠加（OBS 浏览器源）</h2>
      <p class="muted">输出层中与数字人同屏的叠加效果，16:9 容器示意。</p>
      <div class="stage">
        <ProductCardOverlay
          name="番石榴（示例）"
          origin="潮州官塘"
          price="¥ 19.9 / 箱"
          link-url="https://example.com/product/guava-demo"
        />
      </div>
      <ul class="muted tiny">
        <li><code>/overlay?productId=…</code>：仅商品卡片，透明背景（OBS 源需勾选「允许透明」）</li>
        <li><code>/scene?productId=…</code>：数字人占位区 + 商品卡片，单浏览器源合成</li>
      </ul>
    </section>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 0.5rem;
  font-size: 1.6rem;
}

h2 {
  margin: 0 0 0.5rem;
  font-size: 1.1rem;
}

.hero strong {
  color: var(--accent);
}

.status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin-top: 0.9rem;
}

.badge {
  padding: 0.15rem 0.6rem;
  border-radius: 999px;
  border: 1px solid var(--border);
  font-size: 0.82rem;
}

.badge.ok {
  border-color: var(--success);
  color: var(--success);
}

.badge.bad {
  border-color: var(--warning);
  color: var(--warning);
}

.warn {
  color: var(--warning);
}

.rows {
  display: flex;
  flex-direction: column;
}

.row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.4rem 0;
  border-bottom: 1px dashed var(--border);
  font-size: 0.9rem;
}

.row:last-child {
  border-bottom: 0;
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

.tiny {
  font-size: 0.82rem;
  margin-top: 0.6rem;
}

code {
  font-size: 0.84rem;
  word-break: break-all;
}
</style>
