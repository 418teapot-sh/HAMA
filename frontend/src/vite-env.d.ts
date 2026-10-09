/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 브라우저가 API 를 부를 주소. 비우면 지금 주소(개발 서버 프록시)로 부릅니다. 배포에는 https://api.todohama.site */
  readonly VITE_API_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
