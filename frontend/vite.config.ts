import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        // LOCAL-ONLY workaround, deliberately not committed: this machine's Oracle
        // TNSLSNR service permanently occupies 8080, so api-gateway runs on 8095 here.
        // Do not push this change - see start-all-services.ps1 for the matching override.
        target: 'http://localhost:8095',
        changeOrigin: true,
      },
      // Uploaded files (product images, etc.) are served by each owning service at /uploads/**,
      // outside the /api/v1 prefix - routed by the gateway (see api-gateway's application.yml,
      // e.g. Path=/api/v1/inventory/**,/uploads/products/**), so it needs the same local target
      // override as /api above.
      '/uploads': {
        target: 'http://localhost:8095',
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/test/setup.ts',
    coverage: {
      reporter: ['text', 'cobertura'],
      exclude: ['node_modules/', 'src/test/'],
    },
  },
})
