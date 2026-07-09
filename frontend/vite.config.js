import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
    plugins: [react(), tailwindcss()],
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            // OAuth2 routes must go directly to backend
            // They need real browser redirects, not proxied fetch calls
            '/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true
            },
            '/login/oauth2': {
                target: 'http://localhost:8080',
                changeOrigin: true
            }
        }
    }
})