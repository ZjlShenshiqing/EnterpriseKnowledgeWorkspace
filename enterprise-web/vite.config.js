import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api/workbench': 'http://localhost:8098',
      '/api/kb': 'http://localhost:8098',
      '/api/auth': 'http://localhost:8098',
      '/api': 'http://localhost:8098',
      '/mcp': 'http://localhost:8083',
    }
  }
})
