/**
 * 与后端 REST /api/v1 契约一一对应。
 * 契约来源：后端 README 第 3 节 + SessionController / SessionService。
 */

export type SessionStatus = 'CREATED' | 'LIVE' | 'PAUSED' | 'ENDED'

export type PlatformType = 'CUSTOM' | 'BILIBILI' | 'DOUYIN' | 'KUAISHOU' | 'WECHAT'

export const PLATFORM_LABELS: Record<PlatformType, string> = {
  CUSTOM: '通用/其他',
  BILIBILI: '哔哩哔哩',
  DOUYIN: '抖音',
  KUAISHOU: '快手',
  WECHAT: '微信视频号',
}

export const SESSION_STATUS_LABELS: Record<SessionStatus, string> = {
  CREATED: '未开始',
  LIVE: '直播中',
  PAUSED: '已暂停',
  ENDED: '已结束',
}

export type MessageType = 'COMMENT' | 'REPLY' | 'SYSTEM'

/** GET /sessions 元素 —— SessionService.SessionView */
export interface Session {
  id: number
  name: string
  status: SessionStatus
  platform: PlatformType
  rtmpUrl: string | null
  persona: string | null
  greeting: string | null
  ttsVoice: string | null
  autoReply: boolean
  replyPrefix: string | null
  minReplyIntervalSec: number | null
  maxHistory: number | null
  messageCount: number
  queueSize: number
  busy: boolean
  wsConnections: number
  startedAt: string | null
  endedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** POST/PUT /sessions 请求体 —— SessionService.UpsertReq */
export interface UpsertSessionReq {
  name: string
  persona?: string | null
  greeting?: string | null
  platform?: PlatformType | null
  rtmpUrl?: string | null
  autoReply?: boolean
  replyPrefix?: string | null
  ttsVoice?: string | null
  minReplyIntervalSec?: number | null
  maxHistory?: number | null
}

/** 消息流水 —— ChatMessage 实体 */
export interface ChatMessage {
  id: number
  sessionId: number
  type: MessageType
  userName: string | null
  content: string
  audioPath: string | null
  audioDurationMs: number | null
  replyToId: number | null
  createdAt: string | null
}

/** GET /sessions/{id}/messages */
export interface MessagePage {
  items: ChatMessage[]
  hasMore: boolean
  total: number
}

/** GET /sessions/{id}/setup —— OBS / DH_live / eb_stream 接线指引 */
export interface BroadcastSetup {
  consoleUrl: string
  digitalHumanPageUrl: string
  ebStreamUrl: string
  rtmpHint: string
  obsHint: string
}

// ---------------------------------------------------------------------------
// WebSocket 下行帧：ws://host:8080/ws/live/{sessionId}
// 协议见后端 WebSocketLiveHandler 类注释
// ---------------------------------------------------------------------------

export interface CommentFrame {
  type: 'comment'
  sessionId: number
  userName?: string | null
  content: string
  messageId?: number
}

export interface ReplyStartFrame {
  type: 'reply_start'
  sessionId: number
  replyId: number
}

export interface ReplyChunkFrame {
  type: 'reply_chunk'
  sessionId: number
  replyId: number
  text?: string
  endpoint?: boolean
  durationMs?: number
  mime?: string
  audio?: string
}

export interface ReplyErrorFrame {
  type: 'reply_error'
  sessionId: number
  replyId: number
  message?: string
}

export interface SessionEventFrame {
  type: 'session_event'
  sessionId: number
  event: 'started' | 'paused' | 'ended' | string
  message?: string
}

export interface ObsEventFrame {
  type: 'obs_event'
  sessionId?: number
  [key: string]: unknown
}

export interface LiveErrorFrame {
  type: 'error'
  message?: string
}

export interface PongFrame {
  type: 'pong'
}

export type LiveFrame =
  | CommentFrame
  | ReplyStartFrame
  | ReplyChunkFrame
  | ReplyErrorFrame
  | SessionEventFrame
  | ObsEventFrame
  | LiveErrorFrame
  | PongFrame
