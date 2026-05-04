import { defineStore } from 'pinia'
import { ref } from 'vue'

export type DanmakuItem = {
  id: string
  user: string
  text: string
  ts: number
}

export const useStudioStore = defineStore('studio', () => {
  const streaming = ref(false)
  const viewerCount = ref(128)
  const danmaku = ref<DanmakuItem[]>([])

  function toggleStreaming() {
    streaming.value = !streaming.value
  }

  function pushDanmaku(item: Omit<DanmakuItem, 'id'>) {
    danmaku.value = [
      {
        id: `${item.ts}-${Math.random().toString(16).slice(2)}`,
        ...item,
      },
      ...danmaku.value,
    ].slice(0, 200)
  }

  return { streaming, viewerCount, danmaku, toggleStreaming, pushDanmaku }
})
