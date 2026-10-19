import { http } from '@/api/client'
import type {
  BroadcastSetup,
  ChatMessage,
  MessagePage,
  Session,
  UpsertSessionReq,
} from '@/types/live'

/**
 * 直播场次 REST（后端 SessionController，前缀 /api/v1）。
 * 状态机：CREATED --start--> LIVE --pause--> PAUSED --start--> LIVE --stop--> ENDED
 */

export function listSessions(): Promise<Session[]> {
  return http.get<Session[]>('/sessions')
}

export function createSession(req: UpsertSessionReq): Promise<Session> {
  return http.post<Session>('/sessions', req)
}

export function getSession(id: number): Promise<Session> {
  return http.get<Session>(`/sessions/${id}`)
}

export function updateSession(id: number, req: UpsertSessionReq): Promise<Session> {
  return http.put<Session>(`/sessions/${id}`, req)
}

export function deleteSession(id: number): Promise<void> {
  return http.del<void>(`/sessions/${id}`)
}

export function startSession(id: number): Promise<Session> {
  return http.post<Session>(`/sessions/${id}/start`)
}

export function pauseSession(id: number): Promise<Session> {
  return http.post<Session>(`/sessions/${id}/pause`)
}

export function stopSession(id: number): Promise<Session> {
  return http.post<Session>(`/sessions/${id}/stop`)
}

/** 中断当前正在生成的 AI 回复 */
export function interruptSession(id: number): Promise<void> {
  return http.post<void>(`/sessions/${id}/interrupt`)
}

/** 游标拉取消息流水：afterId = 上页最后一条 id，limit ≤ 200 */
export function fetchMessages(id: number, afterId = 0, limit = 50): Promise<MessagePage> {
  return http.get<MessagePage>(`/sessions/${id}/messages?afterId=${afterId}&limit=${limit}`)
}

/** 入站弹幕（平台网关 / 控制台 / 模拟器统一入口），会触发 AI 回复 */
export function postComment(id: number, userName: string, content: string): Promise<ChatMessage> {
  return http.post<ChatMessage>(`/sessions/${id}/comments`, { userName, content })
}

/** OBS / DH_live / eb_stream 接线指引 */
export function fetchSetup(id: number): Promise<BroadcastSetup> {
  return http.get<BroadcastSetup>(`/sessions/${id}/setup`)
}
