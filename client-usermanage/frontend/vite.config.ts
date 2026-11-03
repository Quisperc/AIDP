import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 前后端完全分离：产物只进 frontend/dist，由 EdgeOne Pages 托管，
  // 不再打进 jar。后端只提供 REST API。
  build: {
    outDir: 'dist',
    emptyOutDir: true,
    assetsDir: 'assets',
    sourcemap: false,
  },
  server: {
    port: 5173,
    // 开发态把接口代理到后端，避免跨域
    proxy: {
      '/api': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/oauth2': { target: 'http://127.0.0.1:8081', changeOrigin: true },
      '/logout': { target: 'http://127.0.0.1:8081', changeOrigin: true },
    },
  },
})
