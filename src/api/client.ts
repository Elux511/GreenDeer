import { API_PREFIX, apiBaseUrl } from '@/config'

/**
 * 后端统一响应包装：{ code, message, data }，code === 0 表示成功。
 * 见后端 common/ApiResponse.java。
 */
interface Envelope<T> {
  code: number
  message: string
  data: T
}

export class ApiError extends Error {
  readonly code: number
  readonly status: number

  constructor(message: string, code = -1, status = 0) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function isApiConfigured(): boolean {
  return apiBaseUrl() !== ''
}

function buildUrl(path: string): string {
  const base = apiBaseUrl()
  if (!base) {
    throw new ApiError('未配置 VITE_API_BASE_URL，无法访问后端（请复制 .env.example 为 .env）')
  }
  const suffix = path.startsWith('/') ? path : `/${path}`
  return `${base}${API_PREFIX}${suffix}`
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const url = buildUrl(path)
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (init?.body !== undefined && init.body !== null) {
    headers['Content-Type'] = 'application/json'
  }
  Object.assign(headers, (init?.headers as Record<string, string> | undefined) ?? {})

  let res: Response
  try {
    res = await fetch(url, { ...init, headers })
  } catch (e) {
    throw new ApiError(`后端不可达：${e instanceof Error ? e.message : String(e)}`)
  }

  const raw = await res.text()
  let envelope: Envelope<T> | null = null
  if (raw) {
    try {
      const parsed: unknown = JSON.parse(raw)
      if (parsed && typeof parsed === 'object' && typeof (parsed as Envelope<T>).code === 'number') {
        envelope = parsed as Envelope<T>
      }
    } catch {
      // 非 JSON 响应（如 NDJSON 流），交给调用方按原始文本处理
    }
  }

  if (!res.ok) {
    const message = envelope?.message || `HTTP ${res.status}`
    throw new ApiError(message, envelope?.code ?? res.status, res.status)
  }
  if (envelope) {
    if (envelope.code !== 0) {
      throw new ApiError(envelope.message || '业务处理失败', envelope.code, res.status)
    }
    return envelope.data
  }
  return raw as unknown as T
}

export const http = {
  get<T>(path: string): Promise<T> {
    return request<T>(path, { method: 'GET' })
  },
  post<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, {
      method: 'POST',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },
  put<T>(path: string, body?: unknown): Promise<T> {
    return request<T>(path, {
      method: 'PUT',
      body: body === undefined ? undefined : JSON.stringify(body),
    })
  },
  del<T>(path: string): Promise<T> {
    return request<T>(path, { method: 'DELETE' })
  },
}

/** 把任意异常转成可展示的中文提示 */
export function errorText(e: unknown): string {
  if (e instanceof ApiError) return e.message
  if (e instanceof Error) return e.message
  return String(e)
}
