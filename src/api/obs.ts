import { http } from '@/api/client'
import type { ObsConnectReq, ObsSceneList, ObsStatus, ObsStreamStatus } from '@/types/obs'

/**
 * OBS 集成 REST（后端 ObsController，前缀 /api/v1/obs）。
 * 前提：OBS 菜单 工具 → WebSocket 服务器设置 已开启（默认 4455）。
 */

export function fetchObsStatus(): Promise<ObsStatus> {
  return http.get<ObsStatus>('/obs/status')
}

export function connectObs(req: ObsConnectReq = {}): Promise<void> {
  return http.post<void>('/obs/connect', req)
}

export function disconnectObs(): Promise<void> {
  return http.post<void>('/obs/disconnect')
}

export function fetchObsScenes(): Promise<ObsSceneList> {
  return http.get<ObsSceneList>('/obs/scenes')
}

export function switchObsScene(sceneName: string): Promise<void> {
  return http.post<void>('/obs/scenes/switch', { sceneName })
}

export function fetchObsStreamStatus(): Promise<ObsStreamStatus> {
  return http.get<ObsStreamStatus>('/obs/stream/status')
}

export function startObsStream(): Promise<void> {
  return http.post<void>('/obs/stream/start')
}

export function stopObsStream(): Promise<void> {
  return http.post<void>('/obs/stream/stop')
}
