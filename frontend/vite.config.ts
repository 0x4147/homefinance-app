import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Mirrors the Nginx /api proxy used in compose and Kubernetes
    proxy: {
      '/api': 'http://localhost:8585',
    },
  },
})
