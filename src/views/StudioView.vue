<script setup lang="ts">
import { computed, ref } from 'vue'
import { useStudioStore } from '@/stores/studio'

const studio = useStudioStore()
const draft = ref('')

const canSend = computed(() => draft.value.trim().length > 0)

function pushTestDanmaku() {
  const text = draft.value.trim()
  if (!text) return
  studio.pushDanmaku({ user: '测试观众', text, ts: Date.now() })
  draft.value = ''
}
</script>

<template>
  <div class="stack">
    <section class="card">
      <h1>直播控制台</h1>
      <p class="muted">
        对接后端 WebSocket 后，此处展示实时弹幕与观众指标。下方为本地占位数据，用于联调 UI。
      </p>
    </section>

    <div class="grid2">
      <section class="card">
        <h2>会话状态</h2>
        <div class="row">
          <span class="muted">推流状态</span>
          <span :class="studio.streaming ? 'ok' : 'idle'">
            {{ studio.streaming ? '进行中（占位）' : '未开始' }}
          </span>
        </div>
        <div class="row">
          <span class="muted">在线观众（模拟）</span>
          <span>{{ studio.viewerCount }}</span>
        </div>
        <div class="actions">
          <button type="button" class="btn primary" @click="studio.toggleStreaming">
            {{ studio.streaming ? '结束推流（占位）' : '开始推流（占位）' }}
          </button>
        </div>
      </section>

      <section class="card">
        <h2>测试弹幕</h2>
        <p class="muted">模拟一条观众消息进入列表（不等同真实采集）。</p>
        <div class="inline">
          <input v-model="draft" class="input" placeholder="输入测试弹幕…" @keyup.enter="pushTestDanmaku" />
          <button type="button" class="btn primary" :disabled="!canSend" @click="pushTestDanmaku">发送</button>
        </div>
      </section>
    </div>

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
