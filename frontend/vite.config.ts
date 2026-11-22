import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'

/**
 * 与 Java 服务同源：开发代理把 /api 转发到本地 8080，浏览器不直接持有任何服务端凭据。
 * 构建产物输出到 dist/，由交付阶段决定如何托管（05-03），本阶段不引入额外服务器。
 */
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  },
  build: {
    outDir: 'dist',
    emptyOutDir: true
  },
  test: {
    environment: 'node',
    // 保留 CSS 原始文本，供共享令牌源合同测试读取。
    css: true,
    include: ['src/**/*.test.ts']
  }
})
