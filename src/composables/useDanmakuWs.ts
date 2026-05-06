import { onUnmounted, reactive, ref } from 'vue'
import { wsDanmakuUrl } from '@/config'
import { useStudioStore } from '@/stores/studio'

export type WsStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

function parseDanmaku(raw: unknown): { user: string; text: string; ts: number } | null {
  if (typeof raw !== 'object' || raw === null) return null
  const o = raw as Record<string, unknown>
  const user = typeof o.user === 'string' ? o.user : typeof o.nickname === 'string' ? o.nickname : ''
  const text = typeof o.text === 'string' ? o.text : typeof o.content === 'string' ? o.content : ''
  if (!text) return null
  const tsRaw = o.ts ?? o.time
  const ts =
    typeof tsRaw === 'number'
      ? tsRaw
      : typeof tsRaw === 'string'
        ? Number(tsRaw) || Date.now()
        : Date.now()
  return { user: user || '观众', text, ts }
}

function handlePayload(studio: ReturnType<typeof useStudioStore>, obj: Record<string, unknown>) {
  if (obj.type === 'danmaku' || obj.user || obj.text || obj.content) {
    const inner =
      obj.type === 'danmaku' && typeof obj.payload === 'object' && obj.payload !== null
        ? (obj.payload as Record<string, unknown>)
        : obj
    const row = parseDanmaku(inner)
    if (row) studio.pushDanmaku(row)
  }
}

export function useDanmakuWs() {
  const studio = useStudioStore()
  const status = ref<WsStatus>('idle')
  const lastError = ref<string | null>(null)
  let ws: WebSocket | null = null
  let manualClose = false

  function closeSocket() {
    ws?.close()
    ws = null
  }

  function disconnect() {
    manualClose = true
    status.value = 'closed'
    closeSocket()
  }

  function connect() {
    const url = wsDanmakuUrl()
    if (!url) {
      status.value = 'idle'
      lastError.value = '未配置 VITE_WS_URL'
      return
    }
    closeSocket()
    manualClose = false
    lastError.value = null
    status.value = 'connecting'

    try {
      ws = new WebSocket(url)
    } catch (e) {
      status.value = 'error'
      lastError.value = e instanceof Error ? e.message : String(e)
      return
    }

    ws.onopen = () => {
      status.value = 'open'
    }

    ws.onclose = () => {
      if (!manualClose) status.value = 'closed'
      ws = null
    }

    ws.onerror = () => {
      status.value = 'error'
      lastError.value = 'WebSocket 错误'
    }

    ws.onmessage = (ev) => {
      const raw = ev.data
      if (typeof raw === 'string') {
        try {
          const parsed: unknown = JSON.parse(raw)
          if (typeof parsed === 'object' && parsed !== null) {
            handlePayload(studio, parsed as Record<string, unknown>)
          }
        } catch {
          studio.pushDanmaku({ user: '观众', text: raw.trim(), ts: Date.now() })
        }
        return
      }
    }
  }

  onUnmounted(() => {
    disconnect()
  })

  return reactive({
    status,
    lastError,
    connect,
    disconnect,
  })
}
