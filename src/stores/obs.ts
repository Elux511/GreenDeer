import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as obsApi from '@/api/obs'
import { errorText } from '@/api/client'
import type { ObsConnectReq, ObsStatus } from '@/types/obs'

/**
 * OBS 面板状态（后端 /api/v1/obs）。
 * 前提：OBS 已开启 WebSocket 服务器（工具 → WebSocket 服务器设置，默认 4455）。
 */
export const useObsStore = defineStore('obs', () => {
  const status = ref<ObsStatus | null>(null)
  const scenes = ref<string[]>([])
  const currentScene = ref('')
  const streaming = ref(false)
  const loading = ref(false)
  const error = ref<string | null>(null)

  const host = ref('127.0.0.1')
  const port = ref(4455)
  const password = ref('')

  const connected = ref(false)

  function applyStatus(s: ObsStatus) {
    status.value = s
    connected.value = Boolean(s.connected)
    host.value = s.defaultHost || host.value
    port.value = s.defaultPort || port.value
    const raw = s.streamStatus
    if (raw && typeof raw === 'object' && 'outputActive' in raw) {
      streaming.value = Boolean((raw as { outputActive?: boolean }).outputActive)
    }
    if (!connected.value) {
      scenes.value = []
      currentScene.value = ''
      streaming.value = false
    }
  }

  async function refreshStatus() {
    try {
      applyStatus(await obsApi.fetchObsStatus())
    } catch (e) {
      error.value = errorText(e)
    }
  }

  async function connect() {
    loading.value = true
    error.value = null
    try {
      const req: ObsConnectReq = { host: host.value, port: Number(port.value), password: password.value }
      await obsApi.connectObs(req)
      await refreshStatus()
      if (connected.value) await refreshScenes()
    } catch (e) {
      error.value = errorText(e)
      connected.value = false
    } finally {
      loading.value = false
    }
  }

  async function disconnect() {
    loading.value = true
    error.value = null
    try {
      await obsApi.disconnectObs()
      await refreshStatus()
    } catch (e) {
      error.value = errorText(e)
    } finally {
      loading.value = false
    }
  }

  async function refreshScenes() {
    try {
      const data = await obsApi.fetchObsScenes()
      const list = Array.isArray(data.scenes) ? data.scenes : []
      scenes.value = list
        .map((s) => (typeof s.sceneName === 'string' ? s.sceneName : ''))
        .filter((name) => name !== '')
      currentScene.value = typeof data.currentProgramSceneName === 'string' ? data.currentProgramSceneName : ''
    } catch (e) {
      error.value = errorText(e)
    }
  }

  async function switchScene(name: string) {
    if (!name) return
    loading.value = true
    try {
      await obsApi.switchObsScene(name)
      currentScene.value = name
    } catch (e) {
      error.value = errorText(e)
    } finally {
      loading.value = false
    }
  }

  async function startStream() {
    loading.value = true
    try {
      await obsApi.startObsStream()
      streaming.value = true
      await refreshStatus()
    } catch (e) {
      error.value = errorText(e)
    } finally {
      loading.value = false
    }
  }

  async function stopStream() {
    loading.value = true
    try {
      await obsApi.stopObsStream()
      streaming.value = false
      await refreshStatus()
    } catch (e) {
      error.value = errorText(e)
    } finally {
      loading.value = false
    }
  }

  return {
    status,
    scenes,
    currentScene,
    streaming,
    loading,
    error,
    host,
    port,
    password,
    connected,
    refreshStatus,
    connect,
    disconnect,
    refreshScenes,
    switchScene,
    startStream,
    stopStream,
  }
})
