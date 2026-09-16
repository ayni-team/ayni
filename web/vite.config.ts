import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    // 0.0.0.0 so that the browser on your machine reaches the container.
    host: '0.0.0.0',
    port: 5173,
    // Inside docker compose the API answers at http://backend:8080. Calling
    // "/api/..." from the application keeps the browser on a single origin, so
    // there is no CORS to think about while developing.
    proxy: {
      '/api': { target: 'http://backend:8080', changeOrigin: true },
      '/actuator': { target: 'http://backend:8080', changeOrigin: true },
    },
  },
});
