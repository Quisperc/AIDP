import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  // 构建产物直接进 Spring Boot 的 static 目录，由后端一并托管，
  // 部署时只有一个 jar，不需要额外起 Node 服务
  build: {
    outDir: '../src/main/resources/static',
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
