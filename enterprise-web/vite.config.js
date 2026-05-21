import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api/workbench': 'http://localhost:8084',
      '/api/kb': 'http://localhost:8083',
      '/api/auth': 'http://localhost:8086',
      '/api': 'http://localhost:8086',
      '/mcp': 'http://localhost:8083',
    }
  }
})
