import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { fetchProductById } from '@/api/products'
import type { Product } from '@/types/product'

/**
 * 从 URL ?productId= 解析当前叠加商品（OBS 浏览器源可固定带参）
 */
export function useRouteProduct() {
  const route = useRoute()
  const product = ref<Product | undefined>(undefined)
  const loading = ref(false)

  async function resolve() {
    const id =
      typeof route.query.productId === 'string' && route.query.productId
        ? route.query.productId
        : 'p1'
    loading.value = true
    try {
      product.value = await fetchProductById(id)
    } finally {
      loading.value = false
    }
  }

  watch(
    () => route.query.productId,
    () => {
      void resolve()
    },
    { immediate: true }
  )

  return { product, loading, resolve }
}
