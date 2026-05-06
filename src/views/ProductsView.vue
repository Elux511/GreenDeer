<script setup lang="ts">
import { useProductStore } from '@/stores/products'

const products = useProductStore()

function pickLive(id: string) {
  products.setCurrentLiveProduct(id)
}
</script>

<template>
  <div class="stack">
    <section class="card">
      <h1>商品数据同步 · 溯源</h1>
      <p class="muted">
        列表来自 <code>GET /api/products</code>（未配置或请求失败时使用本地示例数据）。设为「当前讲解商品」后，与直播控制台、OBS
        <code>/overlay</code> / <code>/scene</code> 参数联动。
      </p>
    </section>

    <p v-if="products.loading" class="muted">加载中…</p>

    <div class="grid2">
      <article v-for="p in products.items" :key="p.id" class="card product">
        <div class="title">{{ p.name }}</div>
        <div class="muted">产地：{{ p.origin }}</div>
        <div class="price">{{ p.price }}</div>
        <div class="row">
          <span v-if="products.currentLiveProductId === p.id" class="live">当前讲解</span>
          <button type="button" class="btn mini" @click="pickLive(p.id)">设为当前讲解</button>
        </div>
        <div class="row foot">
          <a v-if="p.traceUrl && p.traceUrl !== '#'" :href="p.traceUrl" class="link" target="_blank" rel="noreferrer"
            >溯源链接</a
          >
          <span v-else class="muted">溯源链接待配置</span>
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
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  flex-wrap: wrap;
}

.row.foot {
  margin-top: 0.5rem;
  justify-content: flex-end;
}

.live {
  font-size: 0.82rem;
  color: var(--success);
  font-weight: 600;
}

.btn.mini {
  padding: 0.35rem 0.65rem;
  font-size: 0.85rem;
}

.link {
  color: var(--accent);
  text-decoration: none;
}

code {
  font-size: 0.85rem;
}
</style>
