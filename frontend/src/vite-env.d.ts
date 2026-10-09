/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 백엔드 주소. 비우면 http://localhost:8080 */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
