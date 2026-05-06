import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchProducts } from '@/api/products'
import type { Product } from '@/types/product'

export const useProductStore = defineStore('products', () => {
  const items = ref<Product[]>([])
  const loading = ref(false)
  /** 控制台选中的「当前讲解商品」，同步 OBS 浏览器源用的 productId */
  const currentLiveProductId = ref<string>('p1')

  const currentLiveProduct = computed(() => items.value.find((p) => p.id === currentLiveProductId.value))

  async function load() {
    loading.value = true
    try {
      items.value = await fetchProducts()
      if (!items.value.some((p) => p.id === currentLiveProductId.value) && items.value[0]) {
        currentLiveProductId.value = items.value[0].id
      }
    } finally {
      loading.value = false
    }
  }

  function setCurrentLiveProduct(id: string) {
    currentLiveProductId.value = id
  }

  return {
    items,
    loading,
    currentLiveProductId,
    currentLiveProduct,
    load,
    setCurrentLiveProduct,
  }
})
