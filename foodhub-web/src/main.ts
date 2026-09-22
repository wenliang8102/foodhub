import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { ElDrawer } from 'element-plus'
import 'element-plus/es/components/base/style/css'
import 'element-plus/es/components/drawer/style/css'
import 'element-plus/es/components/message/style/css'
import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)
app.component('ElDrawer', ElDrawer)
app.use(createPinia()).use(router).mount('#app')
