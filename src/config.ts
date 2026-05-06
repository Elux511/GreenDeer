/** Vite 注入的公开环境变量 */
export function apiBaseUrl(): string {
  const u = import.meta.env.VITE_API_BASE_URL
  return typeof u === 'string' ? u.replace(/\/$/, '') : ''
}

export function wsDanmakuUrl(): string {
  const u = import.meta.env.VITE_WS_URL
  return typeof u === 'string' ? u.trim() : ''
}

export function appBasePath(): string {
  const b = import.meta.env.BASE_URL || '/'
  return b.endsWith('/') ? b.slice(0, -1) : b
}
