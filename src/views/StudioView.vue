<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useStudioStore } from '@/stores/studio'
import { useObsStore } from '@/stores/obs'
import { useProductStore } from '@/stores/products'
import { useLiveSocket } from '@/composables/useLiveSocket'
import { copyText, overlayUrl, sceneUrl } from '@/composables/obsLinks'
import { errorText, isApiConfigured } from '@/api/client'
import { apiBaseUrl } from '@/config'
import * as sessionsApi from '@/api/sessions'
import * as ttsApi from '@/api/tts'
import { PLATFORM_LABELS, SESSION_STATUS_LABELS } from '@/types/live'
import type { BroadcastSetup, PlatformType } from '@/types/live'
import type { SynthesizeResp, VoiceInfo } from '@/types/tts'

const studio = useStudioStore()
const obs = useObsStore()
const products = useProductStore()
const ws = useLiveSocket()

const configured = isApiConfigured()
const apiBase = apiBaseUrl()

const toast = ref('')
const timelineEl = ref<HTMLElement | null>(null)

// ---- 弹幕发送 ----
const userName = ref('主播测试')
const draft = ref('')
const sending = ref(false)

// ---- 新建场次 ----
const showCreate = ref(false)
const form = reactive({
  name: '新直播间',
  persona: '',
  greeting: '',
  platform: 'CUSTOM' as PlatformType,
  rtmpUrl: '',
  ttsVoice: '',
  autoReply: true,
  maxHistory: 12,
  minReplyIntervalSec: 5,
})

// ---- TTS ----
const voices = ref<VoiceInfo[]>([])
const ttsVoice = ref('')
const ttsText = ref('大家好，欢迎来到青鹿直播间，今天给大家介绍潮州官塘牛肉。')
const ttsBusy = ref(false)
const ttsResult = ref<SynthesizeResp | null>(null)

// ---- 接线指引 ----
const setup = ref<BroadcastSetup | null>(null)

const platformOptions = Object.entries(PLATFORM_LABELS) as [PlatformType, string][]

const statusLabel = computed(() => {
  const s = studio.current
  return s ? SESSION_STATUS_LABELS[s.status] : '—'
})
const canStart = computed(() => {
  const s = studio.current
  return Boolean(s && (s.status === 'CREATED' || s.status === 'PAUSED'))
})
const canPause = computed(() => studio.current?.status === 'LIVE')
const canStop = computed(() => {
  const s = studio.current
  return Boolean(s && s.status !== 'ENDED')
})
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
      return '未连接'
  }
})

function say(text: string) {
  toast.value = text
  window.setTimeout(() => {
    toast.value = ''
  }, 2600)
}

async function doCopy(label: string, text: string) {
  const ok = await copyText(text)
  say(ok ? `已复制：${label}` : '复制失败（浏览器权限）')
}

async function scrollToBottom() {
  await nextTick()
  const el = timelineEl.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadVoices() {
  if (!configured) return
  try {
    voices.value = await ttsApi.listVoices()
    const first = voices.value.find((v) => v.available) ?? voices.value[0]
    if (first) {
      ttsVoice.value = first.name
      if (!form.ttsVoice) form.ttsVoice = first.name
    }
  } catch (e) {
    say(`语音列表加载失败：${errorText(e)}`)
  }
}

async function refreshSetup(id: number) {
  try {
    setup.value = await sessionsApi.fetchSetup(id)
  } catch {
    setup.value = null
  }
}

async function refreshAll() {
  if (!configured) return
  await studio.loadSessions()
  void obs.refreshStatus()
}

async function onCreate() {
  if (!configured) return
  const created = await studio.createSession({
    name: form.name.trim() || '新直播间',
    persona: form.persona.trim() || null,
    greeting: form.greeting.trim() || null,
    platform: form.platform,
    rtmpUrl: form.rtmpUrl.trim() || null,
    autoReply: form.autoReply,
    ttsVoice: form.ttsVoice || null,
    maxHistory: form.maxHistory,
    minReplyIntervalSec: form.minReplyIntervalSec,
  })
  if (created) {
    showCreate.value = false
    say(`场次已创建：#${created.id} ${created.name}`)
  } else {
    say(`创建失败：${studio.error ?? '未知错误'}`)
  }
}

async function onStart() {
  if (await studio.startCurrent()) say('场次已开始，AI 互动链路就绪')
  else say(`开始失败：${studio.error ?? '未知错误'}`)
}
async function onPause() {
  if (await studio.pauseCurrent()) say('场次已暂停')
  else say(`暂停失败：${studio.error ?? '未知错误'}`)
}
async function onStop() {
  if (await studio.stopCurrent()) say('场次已结束')
  else say(`结束失败：${studio.error ?? '未知错误'}`)
}
async function onInterrupt() {
  if (await studio.interruptCurrent()) say('已请求中断当前 AI 回复')
  else say(`中断失败：${studio.error ?? '未知错误'}`)
}
async function onDelete(id: number) {
  if (await studio.removeSession(id)) say('场次已删除')
  else say(`删除失败（LIVE 场次需先结束）：${studio.error ?? '未知错误'}`)
}

async function sendDraft() {
  const text = draft.value.trim()
  if (!text || studio.currentId === null) return
  sending.value = true
  try {
    const ok = await studio.sendComment(userName.value.trim() || '观众', text)
    if (ok) {
      draft.value = ''
      void scrollToBottom()
    } else {
      say(`发送失败：${studio.error ?? '未知错误'}`)
    }
  } finally {
    sending.value = false
  }
}

async function testTts() {
  const text = ttsText.value.trim()
  if (!text) return
  ttsBusy.value = true
  try {
    ttsResult.value = await ttsApi.synthesize(text, ttsVoice.value || undefined)
  } catch (e) {
    say(`语音合成失败：${errorText(e)}`)
  } finally {
    ttsBusy.value = false
  }
}

function playTts() {
  if (!ttsResult.value) return
  const audio = new Audio(ttsApi.toAudioDataUrl(ttsResult.value))
  void audio.play()
}

function onProductPick(ev: Event) {
  products.setCurrentLiveProduct((ev.target as HTMLSelectElement).value)
}

watch(
  () => studio.currentId,
  async (id) => {
    studio.clearTimeline()
    setup.value = null
    if (id === null || !configured) {
      ws.disconnect()
      return
    }
    ws.connect(id)
    await studio.loadHistory(id)
    await refreshSetup(id)
    void scrollToBottom()
  },
)

watch(
  () => ws.status,
  (s) => {
    if (s === 'open' && studio.currentId !== null) {
      void studio.syncMessages(studio.currentId)
    }
  },
)

watch(
  () => studio.timeline.length,
  () => {
    void scrollToBottom()
  },
)

onMounted(async () => {
  if (configured) {
    await refreshAll()
    await loadVoices()
  }
})

onUnmounted(() => {
  ws.disconnect()
})
</script>

<template>
  <div class="stack">
    <p v-if="toast" class="toast">{{ toast }}</p>

    <!-- 后端连接状态 -->
    <section class="card">
      <h1>直播控制台</h1>
      <p class="muted">
        本页直接对接 GreenDeer 后端：场次管理（<code>/api/v1/sessions</code>）、实时通道
        （<code>ws://…/ws/live/{id}</code>）、TTS 试音（<code>/api/v1/tts</code>）与 OBS 遥控
        （<code>/api/v1/obs</code>）。
      </p>
      <div class="row">
        <span class="muted">后端地址</span>
        <span>
          <code v-if="apiBase">{{ apiBase }}</code>
          <span v-else class="idle">未配置</span>
        </span>
      </div>
      <p v-if="!configured" class="warn">
        未检测到 <code>VITE_API_BASE_URL</code>。请把项目根目录的 <code>.env.example</code> 复制为
        <code>.env</code>（默认已指向 <code>http://127.0.0.1:8080</code>）后重启 <code>npm run dev</code>。
      </p>
      <p v-else-if="studio.error" class="warn">最近一次错误：{{ studio.error }}</p>
      <div class="actions">
        <button type="button" class="btn" :disabled="!configured" @click="refreshAll">刷新场次</button>
        <button type="button" class="btn primary" :disabled="!configured" @click="showCreate = !showCreate">
          {{ showCreate ? '收起新建' : '新建场次' }}
        </button>
      </div>
    </section>

    <!-- 新建场次 -->
    <section v-if="showCreate" class="card">
      <h2>新建直播场次</h2>
      <div class="form">
        <label>
          <span class="muted">场次名称</span>
          <input v-model="form.name" class="input" placeholder="例如：官塘牛肉专场" />
        </label>
        <label>
          <span class="muted">目标平台</span>
          <select v-model="form.platform" class="select">
            <option v-for="[value, label] in platformOptions" :key="value" :value="value">{{ label }}</option>
          </select>
        </label>
        <label>
          <span class="muted">TTS 音色</span>
          <select v-model="form.ttsVoice" class="select">
            <option value="">后端默认</option>
            <option v-for="v in voices" :key="v.name" :value="v.name">
              {{ v.name }}（{{ v.provider }}{{ v.available ? '' : ' · 不可用' }}）
            </option>
          </select>
        </label>
        <label>
          <span class="muted">RTMP 地址</span>
          <input v-model="form.rtmpUrl" class="input" placeholder="rtmp://…（可留空）" />
        </label>
        <label class="wide">
          <span class="muted">AI 人设（persona）</span>
          <textarea v-model="form.persona" class="input" rows="2" placeholder="留空则用后端默认人设" />
        </label>
        <label class="wide">
          <span class="muted">开场白（greeting）</span>
          <input v-model="form.greeting" class="input" placeholder="可留空" />
        </label>
        <label>
          <span class="muted">上下文轮数 maxHistory</span>
          <input v-model.number="form.maxHistory" class="input" type="number" min="0" max="50" />
        </label>
        <label>
          <span class="muted">最小回复间隔（秒）</span>
          <input v-model.number="form.minReplyIntervalSec" class="input" type="number" min="0" max="120" />
        </label>
        <label class="check wide">
          <input v-model="form.autoReply" type="checkbox" />
          <span>开启自动回复（autoReply）</span>
        </label>
      </div>
      <div class="actions">
        <button type="button" class="btn primary" :disabled="studio.loading" @click="onCreate">创建</button>
      </div>
    </section>

    <!-- 场次与状态机 -->
    <section class="card">
      <h2>当前场次</h2>
      <div class="row">
        <span class="muted">选择场次</span>
        <select
          class="select"
          :value="studio.currentId ?? ''"
          :disabled="!studio.hasSessions"
          @change="studio.selectSession(Number(($event.target as HTMLSelectElement).value) || null)"
        >
          <option v-if="!studio.hasSessions" value="">（暂无场次，请先新建）</option>
          <option v-for="s in studio.sessions" :key="s.id" :value="s.id">
            #{{ s.id }} {{ s.name }} · {{ SESSION_STATUS_LABELS[s.status] }}
          </option>
        </select>
      </div>

      <template v-if="studio.current">
        <div class="row">
          <span class="muted">状态</span>
          <span :class="['badge', studio.current.status]">{{ statusLabel }}</span>
        </div>
        <div class="row">
          <span class="muted">平台 / 音色</span>
          <span>{{ PLATFORM_LABELS[studio.current.platform] }} · {{ studio.current.ttsVoice || '默认' }}</span>
        </div>
        <div class="row">
          <span class="muted">消息 / 队列 / WS</span>
          <span>
            {{ studio.current.messageCount }} 条 · 队列 {{ studio.current.queueSize }} · 连接
            {{ studio.current.wsConnections }}
            <span v-if="studio.current.busy" class="ok"> · 回复中</span>
          </span>
        </div>
        <div class="row">
          <span class="muted">实时通道</span>
          <span :class="ws.status === 'open' ? 'ok' : 'idle'">{{ wsHint }}</span>
        </div>
        <div class="actions">
          <button type="button" class="btn primary" :disabled="!canStart" @click="onStart">
            {{ studio.current.status === 'PAUSED' ? '继续' : '开始' }}
          </button>
          <button type="button" class="btn" :disabled="!canPause" @click="onPause">暂停</button>
          <button type="button" class="btn" :disabled="!canStop" @click="onStop">结束</button>
          <button type="button" class="btn" @click="onInterrupt">中断回复</button>
          <button
            type="button"
            class="btn"
            :disabled="ws.status === 'connecting'"
            @click="studio.currentId !== null && ws.connect(studio.currentId)"
          >
            重连通道
          </button>
          <button
            type="button"
            class="btn"
            :disabled="studio.current.status === 'LIVE'"
            @click="studio.currentId !== null && onDelete(studio.currentId)"
          >
            删除场次
          </button>
        </div>
      </template>
      <p v-else class="muted">尚未选择场次。</p>
    </section>

    <div class="grid2">
      <!-- 弹幕 -->
      <section class="card">
        <h2>发送弹幕（触发 AI 回复）</h2>
        <p class="muted">
          走 <code>POST /api/v1/sessions/{id}/comments</code>，后端落库后经 WS 广播，并在场次 LIVE 且
          autoReply 时进入 AI 回复链路。
        </p>
        <div class="row">
          <span class="muted">观众昵称</span>
          <input v-model="userName" class="input short" />
        </div>
        <div class="inline">
          <input
            v-model="draft"
            class="input"
            placeholder="输入一条弹幕…"
            :disabled="studio.currentId === null"
            @keyup.enter="sendDraft"
          />
          <button
            type="button"
            class="btn primary"
            :disabled="studio.currentId === null || sending || !draft.trim()"
            @click="sendDraft"
          >
            发送
          </button>
        </div>
        <p class="muted tiny">
          提示：后端默认开启弹幕模拟器（<code>app.demo.simulator-enabled</code>），场次开始后会自动投喂弹幕。
        </p>
      </section>

      <!-- TTS -->
      <section class="card">
        <h2>TTS 试音</h2>
        <div class="row">
          <span class="muted">音色</span>
          <select v-model="ttsVoice" class="select">
            <option value="">后端默认</option>
            <option v-for="v in voices" :key="v.name" :value="v.name">
              {{ v.name }}{{ v.available ? '' : '（不可用）' }}
            </option>
          </select>
        </div>
        <textarea v-model="ttsText" class="input block" rows="2" />
        <div class="actions">
          <button type="button" class="btn primary" :disabled="ttsBusy" @click="testTts">
            {{ ttsBusy ? '合成中…' : '合成' }}
          </button>
          <button type="button" class="btn" :disabled="!ttsResult" @click="playTts">播放</button>
        </div>
        <p v-if="ttsResult" class="muted tiny">
          来自 {{ ttsResult.provider }} · {{ ttsResult.mime }} · {{ ttsResult.sampleRate }}Hz ·
          {{ ttsResult.durationMs }}ms
        </p>
      </section>
    </div>

    <!-- 消息时间线 -->
    <section class="card">
      <h2>弹幕与 AI 回复</h2>
      <div ref="timelineEl" class="timeline">
        <div v-for="item in studio.timeline" :key="item.key" :class="['msg', item.kind]">
          <template v-if="item.kind === 'comment'">
            <span class="who">{{ item.userName }}</span>
            <span class="txt">{{ item.content }}</span>
          </template>
          <template v-else-if="item.kind === 'reply'">
            <span class="who reply-who">AI 回复</span>
            <span class="txt">
              {{ item.text || '…' }}
              <span v-if="item.error" class="warn">{{ item.error }}</span>
              <span v-else-if="!item.done" class="muted">（生成中）</span>
            </span>
            <audio v-if="item.audioUrl" :src="item.audioUrl" controls class="audio" />
          </template>
          <template v-else>
            <span class="sys">{{ item.text }}</span>
          </template>
        </div>
      </div>
      <p v-if="studio.timeline.length === 0" class="muted">
        暂无消息。选择/创建场次后开始，消息会经 WebSocket 实时推送过来。
      </p>
    </section>

    <!-- OBS -->
    <section class="card">
      <h2>OBS 遥控（obs-websocket v5）</h2>
      <p class="muted">
        先在 OBS 菜单「工具 → WebSocket 服务器设置」开启（默认端口 4455），再在此连接，即可切场景与启停推流。
      </p>
      <div class="inline">
        <input v-model="obs.host" class="input short" placeholder="主机" />
        <input v-model.number="obs.port" class="input tiny-input" type="number" placeholder="端口" />
        <input v-model="obs.password" class="input short" type="password" placeholder="密码（可空）" />
        <button type="button" class="btn primary" :disabled="obs.loading" @click="obs.connect">连接</button>
        <button type="button" class="btn" :disabled="!obs.connected" @click="obs.disconnect">断开</button>
      </div>
      <div class="row">
        <span class="muted">连接状态</span>
        <span :class="obs.connected ? 'ok' : 'idle'">{{ obs.connected ? '已连接 OBS' : '未连接' }}</span>
      </div>
      <div class="row">
        <span class="muted">推流状态</span>
        <span :class="obs.streaming ? 'ok' : 'idle'">{{ obs.streaming ? '推流中' : '未推流' }}</span>
      </div>
      <div class="inline" v-if="obs.connected">
        <select
          class="select"
          :value="obs.currentScene"
          @change="obs.switchScene(($event.target as HTMLSelectElement).value)"
        >
          <option value="">{{ obs.scenes.length ? '选择场景' : '（未取到场景列表）' }}</option>
          <option v-for="name in obs.scenes" :key="name" :value="name">{{ name }}</option>
        </select>
        <button type="button" class="btn" :disabled="obs.loading" @click="obs.refreshScenes">刷新场景</button>
        <button type="button" class="btn primary" :disabled="obs.streaming || obs.loading" @click="obs.startStream">
          开始推流
        </button>
        <button type="button" class="btn" :disabled="!obs.streaming || obs.loading" @click="obs.stopStream">
          停止推流
        </button>
      </div>
      <p v-if="obs.error" class="warn">{{ obs.error }}</p>
      <p v-else-if="obs.currentScene" class="muted tiny">当前场景：{{ obs.currentScene }}</p>
    </section>

    <!-- 接线指引 -->
    <section class="card">
      <h2>接线指引（OBS / DH_live / eb_stream）</h2>
      <template v-if="setup">
        <div class="row">
          <span class="muted">后端控制台</span>
          <span><code>{{ setup.consoleUrl }}</code></span>
        </div>
        <div class="row">
          <span class="muted">数字人页面</span>
          <span>
            <code v-if="setup.digitalHumanPageUrl">{{ setup.digitalHumanPageUrl }}</code>
            <span v-else class="idle">未配置（后端 app.demo.digital-human-page-url）</span>
          </span>
        </div>
        <div class="row">
          <span class="muted">eb_stream</span>
          <span><code>{{ setup.ebStreamUrl }}</code></span>
        </div>
        <p class="muted tiny">{{ setup.rtmpHint }}</p>
        <p class="muted tiny">{{ setup.obsHint }}</p>
      </template>
      <p v-else class="muted">选择场次后展示后端下发的接线指引。</p>

      <div class="obs-row">
        <label class="muted" for="pick">当前讲解商品（前端本地示例）</label>
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
        <code>/overlay</code>：仅商品卡片（OBS 源需勾选「允许透明」）。<code>/scene</code>：数字人占位区 +
        商品卡片。商品库不在后端 MVP 内，这里用前端本地示例数据；后端补 <code>GET /api/v1/products</code>
        后可直接切换为真实数据。
      </p>
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
  gap: 0.75rem;
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

.warn {
  color: var(--warning);
  font-size: 0.9rem;
}

.badge {
  padding: 0.1rem 0.6rem;
  border-radius: 999px;
  border: 1px solid var(--border);
  font-size: 0.82rem;
}

.badge.LIVE {
  border-color: var(--success);
  color: var(--success);
}

.badge.PAUSED {
  border-color: var(--warning);
  color: var(--warning);
}

.badge.ENDED {
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
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.5rem;
  align-items: center;
}

.input,
.select {
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  border: 1px solid var(--border);
  background: #0c1017;
  color: var(--text);
  font-size: 0.92rem;
  flex: 1;
  min-width: 0;
}

.input.short {
  flex: 0 0 160px;
}

.input.tiny-input {
  flex: 0 0 100px;
}

.input.block {
  width: 100%;
  margin-top: 0.5rem;
  font-family: inherit;
}

.form {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  gap: 0.75rem;
}

.form label {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.form label.wide {
  grid-column: 1 / -1;
}

.form label.check {
  flex-direction: row;
  align-items: center;
  gap: 0.5rem;
}

.obs-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.75rem;
  margin: 0.75rem 0;
}

.btn-row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.tiny {
  font-size: 0.82rem;
  margin-top: 0.6rem;
}

code {
  font-size: 0.85rem;
  word-break: break-all;
}

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-height: 380px;
  overflow: auto;
}

.msg {
  display: grid;
  grid-template-columns: 88px 1fr;
  gap: 0.5rem;
  align-items: baseline;
  font-size: 0.92rem;
  padding: 0.3rem 0.5rem;
  border-left: 3px solid var(--border);
  background: #0c1017;
  border-radius: 6px;
}

.msg.comment {
  border-left-color: var(--accent-dim);
}

.msg.reply {
  border-left-color: var(--success);
}

.msg.system {
  grid-template-columns: 1fr;
  color: var(--muted);
  border-left-color: var(--warning);
}

.who {
  color: var(--accent);
  font-weight: 600;
}

.reply-who {
  color: var(--success);
}

.txt {
  word-break: break-word;
}

.audio {
  grid-column: 2;
  width: 100%;
  height: 32px;
  margin-top: 0.25rem;
}
</style>
