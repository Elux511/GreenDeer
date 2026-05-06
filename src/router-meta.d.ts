import 'vue-router'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    /** 为 true 时不显示顶栏（OBS 全屏/透明页） */
    minimal?: boolean
  }
}
