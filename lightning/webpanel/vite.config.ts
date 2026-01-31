import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'path';

// Source lives in src/main/frontend, build outputs to META-INF/resources for Quarkus to serve
export default defineConfig({
  plugins: [react()],
  base: '/admin/dashboard/',
  build: {
    outDir: path.resolve(__dirname, '../resources/META-INF/resources/admin/dashboard'),
    emptyOutDir: true,
  },
  server: {
    proxy: {
      // Auth endpoints go to control plane (which proxies to auth service)
      '/api/auth': 'http://localhost:8081',
      // Control plane endpoints
      '/api/control-plane': 'http://localhost:8081',
      // Node proxy endpoints
      '/api/nodes': 'http://localhost:8081',
      // Engine endpoints (containers, modules, etc.)
      '/api': 'http://localhost:8080',
      // WebSocket endpoints
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
});
