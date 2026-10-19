import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import * as sessionsApi from '@/api/sessions'
import { errorText } from '@/api/client'
import type { ChatMessage, LiveFrame, Session, UpsertSessionReq } from '@/types/live'

export interface CommentItem {
  kind: 'comment'
  key: string
  userName: string
  content: string
  ts: number
}

export interface ReplyItem {
  kind: 'reply'
  key: string
  replyId: number
  text: string
  audioUrl: string | null
  mime: string | null
  done: boolean
  error: string | null
  ts: number
}

export interface SystemItem {
  kind: 'system'
  key: string
  text: string
  ts: number
}

export type TimelineItem = CommentItem | ReplyItem | SystemItem

const MAX_TIMELINE = 500

function tsOf(value: string | null | undefined): number {
  if (!value) return Date.now()
  const t = Date.parse(value)
  return Number.isNaN(t) ? Date.now() : t
}

/**
 * 直播控制台状态：场次列表 + 当前场次 + 消息时间线。
 * 数据来源：REST /api/v1/sessions* 与 WebSocket /ws/live/{id}（帧经 applyFrame 合入）。
 */
export const useStudioStore = defineStore('studio', () => {
  const sessions = ref<Session[]>([])
  const currentId = ref<number | null>(null)
  const timeline = ref<TimelineItem[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  /** 已合入时间线的最新消息 id，用于 /messages?afterId= 增量补齐 */
  const lastMessageId = ref(0)

  const current = computed<Session | null>(
    () => sessions.value.find((s) => s.id === currentId.value) ?? null,
  )
  const hasSessions = computed(() => sessions.value.length > 0)
  const isLive = computed(() => current.value?.status === 'LIVE')

  function replaceSession(s: Session) {
    const rest = sessions.value.filter((x) => x.id !== s.id)
    sessions.value = [...rest, s].sort((a, b) => a.id - b.id)
  }

  function pushSystem(text: string) {
    const item: SystemItem = {
      kind: 'system',
      key: `s-${Date.now()}-${Math.random().toString(16).slice(2, 8)}`,
      text,
      ts: Date.now(),
    }
    timeline.value = [...timeline.value, item].slice(-MAX_TIMELINE)
  }

  function toTimelineItem(m: ChatMessage): TimelineItem {
    const ts = tsOf(m.createdAt)
    if (m.type === 'COMMENT') {
      return {
        kind: 'comment',
        key: `c-${m.id}`,
        userName: m.userName || '观众',
        content: m.content,
        ts,
      }
    }
    if (m.type === 'REPLY') {
      return {
        kind: 'reply',
        key: `r-${m.id}`,
        replyId: m.id,
        text: m.content,
        audioUrl: null,
        mime: null,
        done: true,
        error: null,
        ts,
      }
    }
    return { kind: 'system', key: `s-${m.id}`, text: m.content, ts }
  }

  // ---------------------------------------------------------------- 场次

  async function loadSessions() {
    loading.value = true
    error.value = null
    try {
      sessions.value = await sessionsApi.listSessions()
      const ids = sessions.value.map((s) => s.id)
      if (currentId.value === null || !ids.includes(currentId.value)) {
        currentId.value = ids.length > 0 ? ids[0] : null
      }
    } catch (e) {
      error.value = errorText(e)
    } finally {
      loading.value = false
    }
  }

  async function refreshCurrent() {
    if (currentId.value === null) return
    try {
      replaceSession(await sessionsApi.getSession(currentId.value))
    } catch (e) {
      error.value = errorText(e)
    }
  }

  /** 切换当前场次并清空时间线（由视图负责重连 WS 与拉历史） */
  function selectSession(id: number | null) {
    if (currentId.value === id) return
    currentId.value = id
    timeline.value = []
    lastMessageId.value = 0
  }

  async function createSession(req: UpsertSessionReq): Promise<Session | null> {
    loading.value = true
    error.value = null
    try {
      const s = await sessionsApi.createSession(req)
      replaceSession(s)
      selectSession(s.id)
      return s
    } catch (e) {
      error.value = errorText(e)
      return null
    } finally {
      loading.value = false
    }
  }

  async function updateSessionInfo(req: UpsertSessionReq): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      replaceSession(await sessionsApi.updateSession(currentId.value, req))
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  async function startCurrent(): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      replaceSession(await sessionsApi.startSession(currentId.value))
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  async function pauseCurrent(): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      replaceSession(await sessionsApi.pauseSession(currentId.value))
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  async function stopCurrent(): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      replaceSession(await sessionsApi.stopSession(currentId.value))
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  async function interruptCurrent(): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      await sessionsApi.interruptSession(currentId.value)
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  async function removeSession(id: number): Promise<boolean> {
    try {
      await sessionsApi.deleteSession(id)
      sessions.value = sessions.value.filter((s) => s.id !== id)
      if (currentId.value === id) selectSession(sessions.value[0]?.id ?? null)
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  // ---------------------------------------------------------------- 消息

  /** 拉取完整历史（首次进入场次时） */
  async function loadHistory(id: number) {
    try {
      const page = await sessionsApi.fetchMessages(id, 0, 200)
      timeline.value = page.items.map(toTimelineItem).slice(-MAX_TIMELINE)
      const last = page.items.length > 0 ? page.items[page.items.length - 1].id : 0
      lastMessageId.value = Math.max(lastMessageId.value, last)
    } catch (e) {
      error.value = errorText(e)
    }
  }

  /** 增量补齐（WS 重连后调用） */
  async function syncMessages(id: number) {
    try {
      const page = await sessionsApi.fetchMessages(id, lastMessageId.value, 200)
      for (const m of page.items) {
        mergeMessage(m)
      }
    } catch {
      // 静默：WS 正常时无需依赖此补齐
    }
  }

  function mergeMessage(m: ChatMessage) {
    const key = m.type === 'REPLY' ? `r-${m.id}` : m.type === 'COMMENT' ? `c-${m.id}` : `s-${m.id}`
    if (timeline.value.some((it) => it.key === key)) return
    if (m.type === 'REPLY' && timeline.value.some((it) => it.kind === 'reply' && it.replyId === m.id)) return
    timeline.value = [...timeline.value, toTimelineItem(m)].slice(-MAX_TIMELINE)
    lastMessageId.value = Math.max(lastMessageId.value, m.id)
  }

  /** 发送弹幕（REST 入口；回显由 WS 广播推送） */
  async function sendComment(userName: string, content: string): Promise<boolean> {
    if (currentId.value === null) return false
    try {
      await sessionsApi.postComment(currentId.value, userName, content)
      return true
    } catch (e) {
      error.value = errorText(e)
      return false
    }
  }

  // ---------------------------------------------------------------- WS 帧

  function upsertReply(replyId: number): ReplyItem {
    const key = `r-${replyId}`
    const found = timeline.value.find((it) => it.kind === 'reply' && it.key === key)
    if (found && found.kind === 'reply') return found
    const item: ReplyItem = {
      kind: 'reply',
      key,
      replyId,
      text: '',
      audioUrl: null,
      mime: null,
      done: false,
      error: null,
      ts: Date.now(),
    }
    timeline.value = [...timeline.value, item].slice(-MAX_TIMELINE)
    return item
  }

  function patchReply(replyId: number, patch: Partial<ReplyItem>) {
    timeline.value = timeline.value.map((it) =>
      it.kind === 'reply' && it.replyId === replyId ? { ...it, ...patch } : it,
    )
  }

  /** 处理一条 WS 下行帧 */
  function applyFrame(frame: LiveFrame) {
    switch (frame.type) {
      case 'comment': {
        const key = frame.messageId ? `c-${frame.messageId}` : `c-${Date.now()}`
        if (frame.messageId && timeline.value.some((it) => it.key === key)) return
        const item: CommentItem = {
          kind: 'comment',
          key,
          userName: frame.userName || '观众',
          content: frame.content,
          ts: Date.now(),
        }
        timeline.value = [...timeline.value, item].slice(-MAX_TIMELINE)
        if (frame.messageId) lastMessageId.value = Math.max(lastMessageId.value, frame.messageId)
        break
      }
      case 'reply_start': {
        upsertReply(frame.replyId)
        break
      }
      case 'reply_chunk': {
        const item = upsertReply(frame.replyId)
        const patch: Partial<ReplyItem> = {}
        if (frame.text) patch.text = item.text + frame.text
        if (frame.audio) {
          const mime = frame.mime || 'audio/wav'
          patch.audioUrl = `data:${mime};base64,${frame.audio}`
          patch.mime = mime
        }
        if (frame.endpoint) patch.done = true
        patchReply(frame.replyId, patch)
        break
      }
      case 'reply_error': {
        upsertReply(frame.replyId)
        patchReply(frame.replyId, { done: true, error: frame.message || 'AI 回复失败' })
        break
      }
      case 'session_event': {
        const map: Record<string, string> = {
          started: '场次已开始',
          paused: '场次已暂停',
          ended: '场次已结束',
        }
        pushSystem(`${map[frame.event] || `场次事件：${frame.event}`}${frame.message ? `（${frame.message}）` : ''}`)
        void refreshCurrent()
        break
      }
      case 'obs_event': {
        pushSystem(`OBS 事件：${String(frame.event ?? 'update')}`)
        break
      }
      case 'error': {
        pushSystem(`服务端错误：${frame.message || '未知错误'}`)
        break
      }
      default:
        break
    }
  }

  function clearTimeline() {
    timeline.value = []
    lastMessageId.value = 0
  }

  return {
    // state
    sessions,
    currentId,
    timeline,
    loading,
    error,
    lastMessageId,
    // getters
    current,
    hasSessions,
    isLive,
    // actions
    loadSessions,
    refreshCurrent,
    selectSession,
    createSession,
    updateSessionInfo,
    startCurrent,
    pauseCurrent,
    stopCurrent,
    interruptCurrent,
    removeSession,
    loadHistory,
    syncMessages,
    sendComment,
    applyFrame,
    clearTimeline,
    pushSystem,
  }
})
