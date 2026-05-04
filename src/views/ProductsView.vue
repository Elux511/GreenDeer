<script setup lang="ts">
import { ref } from 'vue'

type Product = {
  id: string
  name: string
  origin: string
  price: string
  traceUrl?: string
}

const items = ref<Product[]>([
  {
    id: 'p1',
    name: '官塘牛肉（示例）',
    origin: '广东潮州 · 湘桥区',
    price: '¥ 128 / 500g',
    traceUrl: '#',
  },
  {
    id: 'p2',
    name: '杨桃（示例）',
    origin: '广东潮州 · 官塘镇',
    price: '¥ 29.9 / 箱',
  },
])
</script>

<template>
  <div class="stack">
    <section class="card">
      <h1>商品与溯源占位</h1>
      <p class="muted">
        后续对接 Spring Boot 商品接口与二维码溯源页。直播画面中可使用叠加层组件挂载当前讲解商品（见
        <code>src/components/ProductCardOverlay.vue</code>）。
      </p>
    </section>

    <div class="grid2">
      <article v-for="p in items" :key="p.id" class="card product">
        <div class="title">{{ p.name }}</div>
        <div class="muted">产地：{{ p.origin }}</div>
        <div class="price">{{ p.price }}</div>
        <div class="row">
          <a v-if="p.traceUrl" :href="p.traceUrl" class="link">溯源（占位）</a>
          <span v-else class="muted">暂无溯源链接</span>
        </div>
      </article>
    </div>
  </div>
</template>

<style scoped>
h1 {
  margin: 0 0 0.5rem;
  font-size: 1.35rem;
}

.product .title {
  font-weight: 700;
  margin-bottom: 0.35rem;
}

.price {
  margin: 0.6rem 0;
  color: var(--warning);
  font-size: 1.15rem;
}

.row {
  display: flex;
  justify-content: flex-end;
}

.link {
  color: var(--accent);
  text-decoration: none;
}

code {
  font-size: 0.85rem;
  color: var(--muted);
}
</style>
