/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Origin of the API when the web is served from another host, with no trailing path:
   * `https://api.example.org`. Empty in development, where Vite proxies `/api`.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
