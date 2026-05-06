import { createRouter, createWebHistory, type RouteLocationNormalized } from 'vue-router'

export const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/studio',
      name: 'studio',
      component: () => import('@/views/StudioView.vue'),
      meta: { title: '直播控制台' },
    },
    {
      path: '/products',
      name: 'products',
      component: () => import('@/views/ProductsView.vue'),
      meta: { title: '商品与溯源' },
    },
    {
      path: '/overlay',
      name: 'overlay',
      component: () => import('@/views/OverlayView.vue'),
      meta: { title: 'OBS商品叠加', minimal: true },
    },
    {
      path: '/scene',
      name: 'scene',
      component: () => import('@/views/SceneView.vue'),
      meta: { title: 'OBS合成场景', minimal: true },
    },
  ],
})

router.afterEach((to: RouteLocationNormalized) => {
  const title = to.meta.title as string | undefined
  document.title = title ? `${title} · 青鹿直播` : '青鹿直播'
})
