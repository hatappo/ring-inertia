import inertia from '@inertiajs/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [inertia(), react()],
  server: {
    cors: true,
    port: 5173,
    strictPort: true,
  },
})
