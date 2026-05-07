import type { Product } from '@/types/product'
import { apiFetch } from '@/api/client'
import { apiBaseUrl } from '@/config'

/** 后端未就绪时的示例数据（与立项演示一致） */
export const MOCK_PRODUCTS: Product[] = [
  {
    id: 'p1',
    name: '官塘牛肉（示例）',
    origin: '广东潮州 · 湘桥区',
    price: '¥ 128 / 500g',
    traceUrl: 'https://example.com/product/demo-beef',
  },
  {
    id: 'p2',
    name: '杨桃（示例）',
    origin: '广东潮州 · 官塘镇',
    price: '¥ 29.9 / 箱',
    traceUrl: 'https://example.com/product/demo-starfruit',
  },
]

export async function fetchProducts(): Promise<Product[]> {
  if (!apiBaseUrl()) {
    return [...MOCK_PRODUCTS]
  }
  try {
    const list = await apiFetch<Product[]>('/api/products')
    return Array.isArray(list) && list.length > 0 ? list : MOCK_PRODUCTS
  } catch {
    return [...MOCK_PRODUCTS]
  }
}

export async function fetchProductById(id: string): Promise<Product | undefined> {
  if (!id) return undefined
  if (!apiBaseUrl()) {
    return MOCK_PRODUCTS.find((p) => p.id === id)
  }
  try {
    return await apiFetch<Product>(`/api/products/${encodeURIComponent(id)}`)
  } catch {
    return MOCK_PRODUCTS.find((p) => p.id === id)
  }
}
