import type { Product } from '@/types/product'

/**
 * 商品数据（演示用）。
 *
 * 注意：商品库不在后端 MVP 范围内 —— 后端 README「8. 演进建议」明确把
 * “形象资产/剧本/商品库/知识库 RAG”列为不在本期实现的功能。
 * 因此这里不请求后端，直接用与立项演示一致的本地示例数据；
 * OBS 叠加层（/overlay、/scene）据此渲染，二维码由 traceUrl 生成。
 *
 * 后续若要接真实商品：在后端补 GET /api/v1/products，再把本文件
 * 换成 http.get<Product[]>('/products') 即可，页面无需改动。
 */
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
  {
    id: 'p3',
    name: '番石榴（示例）',
    origin: '广东潮州 · 官塘镇',
    price: '¥ 19.9 / 箱',
    traceUrl: '',
  },
]

export async function fetchProducts(): Promise<Product[]> {
  return MOCK_PRODUCTS.map((p) => ({ ...p }))
}

export async function fetchProductById(id: string): Promise<Product | undefined> {
  if (!id) return undefined
  return MOCK_PRODUCTS.find((p) => p.id === id)
}
