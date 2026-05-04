<script setup lang="ts">
/**
 * 叠加在预览画布或 OBS 浏览器源上的商品卡片（占位）。
 * 正式环境由父组件传入当前讲解商品与样式变量。
 */
defineProps<{
  name: string
  price: string
  origin?: string
  visible?: boolean
}>()
</script>

<template>
  <div v-if="visible !== false" class="overlay" role="region" aria-label="商品卡片">
    <div class="chip">热卖</div>
    <div class="name">{{ name }}</div>
    <div v-if="origin" class="origin">{{ origin }}</div>
    <div class="price">{{ price }}</div>
  </div>
</template>

<style scoped>
.overlay {
  position: absolute;
  right: 12px;
  bottom: 12px;
  width: min(320px, 72vw);
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
</style>
