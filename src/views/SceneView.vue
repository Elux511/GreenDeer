<script setup lang="ts">
import DigitalHumanPlaceholder from '@/components/DigitalHumanPlaceholder.vue'
import ProductCardOverlay from '@/components/ProductCardOverlay.vue'
import { useRouteProduct } from '@/composables/useRouteProduct'

const { product } = useRouteProduct()
</script>

<template>
  <div class="scene">
    <DigitalHumanPlaceholder />
    <ProductCardOverlay
      v-if="product"
      class="card-pos"
      :name="product.name"
      :price="product.price"
      :origin="product.origin"
      :link-url="product.traceUrl"
    />
  </div>
</template>

<style scoped>
.scene {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  padding: 12px;
  box-sizing: border-box;
  position: relative;
  background:
    radial-gradient(ellipse 80% 60% at 50% 20%, rgba(94, 200, 255, 0.08), transparent),
    linear-gradient(180deg, #0a0e14, #07090d);
}

.card-pos {
  position: absolute;
  right: 12px;
  bottom: 12px;
}
</style>
