/** OBS 集成接口类型（后端 ObsController / ObsService）。后端统一返回 Map，这里只描述用到的字段。 */

export interface ObsStatus {
  connected: boolean
  defaultHost: string
  defaultPort: number
  /** GetVersion 原始响应（连接后才存在） */
  obsVersion?: Record<string, unknown>
  /** GetStreamStatus 原始响应（连接后才存在） */
  streamStatus?: Record<string, unknown>
}

export interface ObsSceneItem {
  sceneName?: string
  sceneIndex?: number
  [key: string]: unknown
}

export interface ObsSceneList {
  currentProgramSceneName?: string
  currentPreviewSceneName?: string
  scenes?: ObsSceneItem[]
  [key: string]: unknown
}

export interface ObsStreamStatus {
  outputActive?: boolean
  outputReconnecting?: boolean
  outputTimecode?: string
  [key: string]: unknown
}

export interface ObsConnectReq {
  host?: string
  port?: number
  password?: string
}
