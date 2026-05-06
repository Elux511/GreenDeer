<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useStudioStore } from '@/stores/studio'
import { useProductStore } from '@/stores/products'
import { useDanmakuWs } from '@/composables/useDanmakuWs'
import { copyText, overlayUrl, sceneUrl } from '@/composables/obsLinks'

const studio = useStudioStore()
const products = useProductStore()
const ws = useDanmakuWs()

const draft = ref('')
const toast = ref('')

const canSend = computed(() => draft.value.trim().length > 0)

const wsHint = computed(() => {
  switch (ws.status) {
    case 'open':
      return '已连接'
    case 'connecting':
      return '连接中…'
    case 'error':
      return ws.lastError || '连接异常'
    case 'closed':
      return '已断开'
    default:
      return '未连接（请配置 .env 中 VITE_WS_URL）'
  }
})

function pushTestDanmaku() {
  const text = draft.value.trim()
  if (!text) return
  studio.pushDanmaku({ user: '测试观众', text, ts: Date.now() })
  draft.value = ''
}

async function doCopy(label: string, url: string) {
  const ok = await copyText(url)
  toast.value = ok ? `已复制：${label}` : '复制失败（浏览器权限）'
  window.setTimeout(() => {
    toast.value = ''
  }, 2500)
}

function onProductPick(ev: Event) {
  const id = (ev.target as HTMLSelectElement).value
  products.setCurrentLiveProduct(id)
}

onMounted(() => {
  ws.connect()
})
</script>

<template>
  <div class="stack">
    <p v-if="toast" class="toast">{{ toast }}</p>

    <section class="card">
      <h1>直播控制台</h1>
      <p class="muted">
        对应架构<strong>输入层</strong>展示：弹幕采集、观众统计；下方连接<strong>弹幕 WebSocket</strong>（<code>VITE_WS_URL</code>），可与 Spring Boot 联调。
      </p>
    </section>

    <div class="grid2">
      <section class="card">
        <h2>会话与观众统计模块（占位）</h2>
        <div class="row">
          <span class="muted">推流状态（本地占位）</span>
          <span :class="studio.streaming ? 'ok' : 'idle'">
            {{ studio.streaming ? '进行中' : '未开始' }}
          </span>
        </div>
        <div class="row">
          <span class="muted">在线观众（模拟）</span>
          <span>{{ studio.viewerCount }}</span>
        </div>
        <div class="row">
          <span class="muted">弹幕 WebSocket</span>
          <span :class="ws.status === 'open' ? 'ok' : 'idle'">{{ wsHint }}</span>
        </div>
        <div class="actions">
          <button type="button" class="btn" @click="ws.connect()">重连 WS</button>
          <button type="button" class="btn primary" @click="studio.toggleStreaming">
            {{ studio.streaming ? '结束推流（占位）' : '开始推流（占位）' }}
          </button>
        </div>
      </section>

      <section class="card">
        <h2>测试弹幕</h2>
        <p class="muted">模拟观众留言进入列表（联调前可用）。</p>
        <div class="inline">
          <input v-model="draft" class="input" placeholder="输入测试弹幕…" @keyup.enter="pushTestDanmaku" />
          <button type="button" class="btn primary" :disabled="!canSend" @click="pushTestDanmaku">发送</button>
        </div>
      </section>
    </div>

    <section class="card">
      <h2>OBS 浏览器源 · 输出层</h2>
      <p class="muted">
        先选当前讲解商品，再复制 URL 粘贴到 OBS「浏览器」源。叠加层页需勾选<strong>允许透明</strong>（仅
        <code>/overlay</code>）。
      </p>
      <div class="obs-row">
        <label class="muted" for="pick">当前讲解商品</label>
        <select
          id="pick"
          class="select"
          :value="products.currentLiveProductId"
          :disabled="products.loading || products.items.length === 0"
          @change="onProductPick"
        >
          <option v-for="p in products.items" :key="p.id" :value="p.id">{{ p.name }}</option>
        </select>
      </div>
      <div class="btn-row">
        <button
          type="button"
          class="btn"
          :disabled="products.items.length === 0"
          @click="doCopy('透明叠加层', overlayUrl(products.currentLiveProductId))"
        >
          复制 /overlay 地址
        </button>
        <button
          type="button"
          class="btn primary"
          :disabled="products.items.length === 0"
          @click="doCopy('合成场景', sceneUrl(products.currentLiveProductId))"
        >
          复制 /scene 地址
        </button>
      </div>
      <p class="muted tiny">
        <code>/overlay</code>：仅商品卡片（透明背景）。<code>/scene</code>：数字人占位区 + 商品卡片，单源合成。
      </p>
    </section>

    <section class="card">
      <h2>弹幕时间线</h2>
      <ul class="timeline">
        <li v-for="d in studio.danmaku" :key="d.id">
          <span class="who">{{ d.user }}</span>
          <span class="txt">{{ d.text }}</span>
          <span class="muted small">{{ new Date(d.ts).toLocaleTimeString() }}</span>
        </li>
      </ul>
      <p v-if="studio.danmaku.length === 0" class="muted">暂无弹幕。</p>
    </section>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 0.5rem;
  font-size: 1.35rem;
}

h2 {
  margin: 0 0 0.75rem;
  font-size: 1.05rem;
}

.toast {
  position: fixed;
  top: 1rem;
  right: 1rem;
  z-index: 50;
  margin: 0;
  padding: 0.5rem 0.85rem;
  border-radius: 10px;
  background: var(--surface);
  border: 1px solid var(--border);
  font-size: 0.9rem;
}

.row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0.35rem 0;
  border-bottom: 1px dashed var(--border);
}

.row:last-of-type {
  border-bottom: 0;
}

.ok {
  color: var(--success);
}

.idle {
  color: var(--muted);
}

.actions {
  margin-top: 0.85rem;
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.inline {
  display: flex;
  gap: 0.5rem;
  margin-top: 0.5rem;
}

.input {
  flex: 1;
  min-width: 0;
  padding: 0.55rem 0.75rem;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #0c1017;
  color: var(--text);
}

.obs-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin: 0.75rem 0;
}

.select {
  min-width: 200px;
  padding: 0.5rem 0.65rem;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #0c1017;
  color: var(--text);
}

.btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.tiny {
  font-size: 0.82rem;
  margin-top: 0.75rem;
}

code {
  font-size: 0.85rem;
}

.timeline {
  list-style: none;
  padding: 0;
  margin: 0;
  display: flex;
  flex-direction: column;
  gap: 0.55rem;
  max-height: 320px;
  overflow: auto;
}

.timeline li {
  display: grid;
  grid-template-columns: 88px 1fr auto;
  gap: 0.5rem;
  align-items: baseline;
  font-size: 0.92rem;
}

.who {
  color: var(--accent);
  font-weight: 600;
}

.txt {
  word-break: break-word;
}

.small {
  font-size: 0.78rem;
}
</style>
