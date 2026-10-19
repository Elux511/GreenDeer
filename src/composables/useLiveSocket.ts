import { onUnmounted, reactive, ref } from 'vue'
import { liveSocketUrl } from '@/config'
import { useStudioStore } from '@/stores/studio'
import type { LiveFrame } from '@/types/live'

export type WsStatus = 'idle' | 'connecting' | 'open' | 'closed' | 'error'

/**
 * 场次实时通道：ws://host:8080/ws/live/{sessionId}
 *
 * 下行帧见后端 WebSocketLiveHandler：
 *   comment / reply_start / reply_chunk / reply_error / session_event / obs_event / error / pong
 * 上行帧：
 *   { type: 'comment', userName, content } —— 入站弹幕（等价于 POST /comments）
 *   { type: 'ping' }                       —— 心跳
 */
export function useLiveSocket() {
  const studio = useStudioStore()
  const status = ref<WsStatus>('idle')
  const lastError = ref<string | null>(null)
  const sessionId = ref<number | null>(null)

  let ws: WebSocket | null = null
  let manualClose = false
  let pingTimer: number | null = null

  function clearPing() {
    if (pingTimer !== null) {
      window.clearInterval(pingTimer)
      pingTimer = null
    }
  }

  function disconnect() {
    clearPing()
    manualClose = true
    if (ws) {
      try {
        ws.close()
      } catch {
        // 忽略关闭异常
      }
      ws = null
    }
    status.value = 'closed'
  }

  function handleFrame(raw: string) {
    let frame: LiveFrame
    try {
      frame = JSON.parse(raw) as LiveFrame
    } catch {
      return
    }
    if (!frame || typeof frame !== 'object' || typeof frame.type !== 'string') return
    if (frame.type === 'pong') return
    studio.applyFrame(frame)
  }

  function connect(id: number) {
    const url = liveSocketUrl(id)
    if (!url) {
      status.value = 'idle'
      lastError.value = '未配置后端地址（VITE_API_BASE_URL / VITE_WS_BASE_URL）'
      return
    }
    disconnect()
    manualClose = false
    lastError.value = null
    sessionId.value = id
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
      clearPing()
      pingTimer = window.setInterval(() => {
        if (ws && ws.readyState === WebSocket.OPEN) {
          ws.send(JSON.stringify({ type: 'ping' }))
        }
      }, 25000)
    }
    ws.onclose = () => {
      clearPing()
      ws = null
      if (!manualClose) status.value = 'closed'
    }
    ws.onerror = () => {
      status.value = 'error'
      lastError.value = '实时通道连接异常'
    }
    ws.onmessage = (ev) => {
      if (typeof ev.data === 'string') handleFrame(ev.data)
    }
  }

  /** 经 WS 上送一条弹幕；返回 false 表示连接不可用（调用方可退回 POST） */
  function sendComment(userName: string, content: string): boolean {
    if (!ws || ws.readyState !== WebSocket.OPEN) return false
    ws.send(JSON.stringify({ type: 'comment', userName, content }))
    return true
  }

  onUnmounted(() => {
    disconnect()
  })

  return reactive({
    status,
    lastError,
    sessionId,
    connect,
    disconnect,
    sendComment,
  })
}
