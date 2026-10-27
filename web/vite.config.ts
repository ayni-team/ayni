import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Node's own types are not a dependency of the web; this is the one global the config reads.
declare const process: { env: Record<string, string | undefined> };

// Where the API answers. Inside docker compose it is http://backend:8080 (the compose file sets
// it); running `npm run dev` on your machine it is the backend that compose publishes on 8080.
const api = process.env.AYNI_API_URL ?? 'http://localhost:8080';

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: { '@': decodeURIComponent(new URL('./src', import.meta.url).pathname) },
  },
  server: {
    // 0.0.0.0 so that the browser on your machine reaches the container.
    host: '0.0.0.0',
    port: 5173,
    // Calling "/api/..." from the application keeps the browser on a single origin, so there is
    // no CORS to think about while developing.
    proxy: {
      '/api': { target: api, changeOrigin: true },
      '/actuator': { target: api, changeOrigin: true },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    css: false,
    restoreMocks: true,
    // Node 25 and later define a localStorage of their own that hides jsdom's.
    poolOptions: { forks: { execArgv: ['--no-experimental-webstorage'] } },
  },
});
