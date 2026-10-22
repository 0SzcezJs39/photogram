import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        // backend слушает context-path /api, поэтому путь НЕ переписываем
        // (иначе /api/photos уходит на /photos и ловит 404)
        target: 'http://localhost:8082',
        changeOrigin: true
      }
    }
  }
})