import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    // In development: Vite forwards any /api request to Spring Boot on 8080
    // This is why you never see CORS errors during development
    // The browser thinks it is talking to Vite (port 5173)
    // Vite secretly forwards it to Spring Boot (port 8080)
    // In production both are on the same domain so no proxy needed
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})