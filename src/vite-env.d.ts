/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

interface ImportMetaEnv {
  /** 后端 REST 基址，如 http://127.0.0.1:8080 */
  readonly VITE_API_BASE_URL?: string
  /** 可选：WebSocket 基址，留空则由 VITE_API_BASE_URL 推导（http→ws） */
  readonly VITE_WS_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
