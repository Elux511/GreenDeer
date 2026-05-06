import { appBasePath } from '@/config'

function absolute(pathWithQuery: string): string {
  const base = appBasePath()
  const path = pathWithQuery.startsWith('/') ? pathWithQuery : `/${pathWithQuery}`
  return `${window.location.origin}${base}${path}`
}

/** 仅商品卡片叠加（透明背景，OBS 浏览器源勾选透明） */
export function overlayUrl(productId: string): string {
  return absolute(`/overlay?productId=${encodeURIComponent(productId)}`)
}

/** 数字人占位区 + 商品卡片（单浏览器源合成） */
export function sceneUrl(productId: string): string {
  return absolute(`/scene?productId=${encodeURIComponent(productId)}`)
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
