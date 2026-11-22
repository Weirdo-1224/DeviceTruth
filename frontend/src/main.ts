import { createApp } from 'vue'
import App from './AuthenticatedWorkbench.vue'
import './design.css'
import './workspace.css'

if (location.pathname === '/admin' || location.pathname.startsWith('/admin/')) {
  void import('./admin/AdminShell.vue').then(({ default: AdminShell }) => createApp(AdminShell).mount('#app'))
} else {
  createApp(App).mount('#app')
}

