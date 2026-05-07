<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import QRCode from 'qrcode'

const props = defineProps<{
  name: string
  price: string
  origin?: string
  visible?: boolean
  /** 外部商品页 / 溯源 / 购买链接（http/https），OBS 场景建议观众扫码打开 */
  linkUrl?: string
}>()

const qrDataUrl = ref<string | null>(null)

function normalizeExternalUrl(raw?: string): string {
  const u = raw?.trim()
  if (!u || u === '#') return ''
  try {
    const parsed = new URL(u)
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? u : ''
  } catch {
    return ''
  }
}

const effectiveLink = computed(() => normalizeExternalUrl(props.linkUrl))

const linkCaption = computed(() => {
  const u = effectiveLink.value
  if (!u) return ''
  try {
    const { hostname, pathname, search } = new URL(u)
    const path = pathname + search
    if (path.length <= 28) return `${hostname}${path}`
    return `${hostname}${path.slice(0, 26)}…`
  } catch {
    return u.length > 36 ? `${u.slice(0, 34)}…` : u
  }
})

watch(
  () => effectiveLink.value,
  async (url) => {
    if (!url) {
      qrDataUrl.value = null
      return
    }
    try {
      qrDataUrl.value = await QRCode.toDataURL(url, {
        width: 180,
        margin: 1,
        color: { dark: '#0d1117', light: '#ffffff' },
        errorCorrectionLevel: 'M',
      })
    } catch {
      qrDataUrl.value = null
    }
  },
  { immediate: true }
)
</script>

<template>
  <div v-if="visible !== false" class="overlay" role="region" aria-label="商品卡片">
    <div class="chip">热卖</div>
    <div class="name">{{ name }}</div>
    <div v-if="origin" class="origin">{{ origin }}</div>
    <div class="price">{{ price }}</div>

    <div v-if="effectiveLink" class="link-block">
      <div class="link-meta">
        <span class="link-label">扫码打开链接</span>
        <span class="link-caption" :title="effectiveLink">{{ linkCaption }}</span>
      </div>
      <img
        v-if="qrDataUrl"
        class="qr"
        :src="qrDataUrl"
        width="88"
        height="88"
        alt=""
        aria-hidden="true"
      />
    </div>
  </div>
</template>

<style scoped>
.overlay {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: min(340px, 85vw);
  padding: 0.75rem 0.9rem;
  border-radius: 14px;
  background: linear-gradient(135deg, rgba(21, 27, 36, 0.92), rgba(15, 20, 28, 0.85));
  border: 1px solid rgba(94, 200, 255, 0.35);
  box-shadow: 0 10px 40px rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(8px);
}

.chip {
  display: inline-block;
  font-size: 0.72rem;
  padding: 0.15rem 0.45rem;
  border-radius: 999px;
  background: rgba(245, 200, 76, 0.18);
  color: var(--warning);
  margin-bottom: 0.35rem;
}

.name {
  font-weight: 800;
  font-size: 1rem;
  margin-bottom: 0.15rem;
}

.origin {
  font-size: 0.85rem;
  color: var(--muted);
  margin-bottom: 0.35rem;
}

.price {
  font-size: 1.2rem;
  color: var(--warning);
  font-weight: 700;
}

.link-block {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.65rem;
  margin-top: 0.65rem;
  padding-top: 0.55rem;
  border-top: 1px solid rgba(94, 200, 255, 0.2);
}

.link-meta {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
  min-width: 0;
}

.link-label {
  font-size: 0.72rem;
  font-weight: 600;
  color: var(--accent);
  letter-spacing: 0.02em;
}

.link-caption {
  font-size: 0.78rem;
  color: var(--muted);
  word-break: break-all;
  line-height: 1.35;
}

.qr {
  flex-shrink: 0;
  width: 88px;
  height: 88px;
  border-radius: 8px;
  border: 1px solid rgba(255, 255, 255, 0.12);
  background: #fff;
}
</style>
