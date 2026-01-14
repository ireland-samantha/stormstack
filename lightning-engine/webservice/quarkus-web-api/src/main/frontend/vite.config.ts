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
      '/api': 'http://localhost:8080',
      '/ws': {
        target: 'ws://localhost:8080',
        ws: true,
      },
    },
  },
});
