/** Vite 注入的公开环境变量（仅浏览器可知的项，不放密钥）。 */

/** 后端 REST 基址，如 http://127.0.0.1:8080（末尾斜杠会被去掉） */
export function apiBaseUrl(): string {
  const u = import.meta.env.VITE_API_BASE_URL
  return typeof u === 'string' ? u.replace(/\/+$/, '') : ''
}

/** 后端 REST 统一前缀（后端 README 第 3 节） */
export const API_PREFIX = '/api/v1'

/**
 * WebSocket 基址：
 * 1) 优先 VITE_WS_BASE_URL（可指向独立网关 / wss）
 * 2) 否则由 API 基址推导（http→ws、https→wss）
 */
export function wsBaseUrl(): string {
  const explicit = import.meta.env.VITE_WS_BASE_URL
  if (typeof explicit === 'string' && explicit.trim()) {
    return explicit.trim().replace(/\/+$/, '')
  }
  const base = apiBaseUrl()
  if (!base) return ''
  return base.replace(/^http/i, 'ws')
}

/** 场次实时通道：ws://host:8080/ws/live/{sessionId} */
export function liveSocketUrl(sessionId: number | string): string {
  const base = wsBaseUrl()
  return base ? `${base}/ws/live/${sessionId}` : ''
}

export function appBasePath(): string {
  const b = import.meta.env.BASE_URL || '/'
  return b.endsWith('/') ? b.slice(0, -1) : b
}
