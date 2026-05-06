<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import ProductCardOverlay from '@/components/ProductCardOverlay.vue'
import { useRouteProduct } from '@/composables/useRouteProduct'

const { product, loading } = useRouteProduct()

onMounted(() => {
  document.documentElement.classList.add('obs-overlay')
})

onUnmounted(() => {
  document.documentElement.classList.remove('obs-overlay')
})
</script>

<template>
  <div class="overlay-root">
    <ProductCardOverlay
      v-if="product"
      :name="product.name"
      :price="product.price"
      :origin="product.origin"
    />
    <div v-else-if="!loading" class="hint muted">未找到商品，请检查 URL 参数 productId</div>
  </div>
</template>

<style scoped>
.overlay-root {
  min-height: 100vh;
  width: 100%;
  position: relative;
  pointer-events: none;
}

.hint {
  position: absolute;
  bottom: 12px;
  right: 12px;
  font-size: 0.8rem;
}
</style>
