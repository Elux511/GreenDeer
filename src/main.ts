import './assets/base.css'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import { useProductStore } from '@/stores/products'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.mount('#app')

void useProductStore().load()
