import { createApp } from 'vue'
import App from './App.vue'
import './design.css'

if (location.pathname === '/admin' || location.pathname.startsWith('/admin/')) {
  void import('./admin/AdminShell.vue').then(({ default: AdminShell }) => createApp(AdminShell).mount('#app'))
} else {
  createApp(App).mount('#app')
}

