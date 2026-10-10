/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 브라우저가 API 를 부를 주소. 비우면 개발 중엔 지금 주소(프록시), 빌드에선 https://api.todohama.site */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
