import { apiBaseUrl } from '@/config'

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const base = apiBaseUrl()
  if (!base) {
    throw new Error('VITE_API_BASE_URL 未配置')
  }
  const url = `${base}${path.startsWith('/') ? path : `/${path}`}`
  const res = await fetch(url, {
    ...init,
    headers: {
      Accept: 'application/json',
      ...init?.headers,
    },
  })
  if (!res.ok) {
    throw new Error(`HTTP ${res.status}`)
  }
  return res.json() as Promise<T>
}
