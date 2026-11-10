import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // Forward API calls to the Spring Boot backend so the browser sees one origin (no CORS setup needed in dev).
    proxy: {
      '/api': 'http://localhost:8080',
      // Realtime events: the same origin as the page, so the backend's Origin check (the frontend URL) passes.
      '/ws': { target: 'ws://localhost:8080', ws: true },
    },
  },
})
