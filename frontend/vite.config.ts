import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// During development the API is proxied so the browser sees a single
// origin: session and CSRF cookies then work exactly as in production,
// where the reverse proxy serves both the app and /api.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: false },
    },
  },
  build: {
    target: 'es2022',
    sourcemap: false,
    chunkSizeWarningLimit: 800,
  },
})
