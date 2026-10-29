/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** 브라우저가 API 를 부를 주소. 비우면 개발 중엔 지금 주소(프록시), 빌드에선 https://api.todohama.site */
  readonly VITE_API_URL?: string
  /** Paddle client-side token (live_ / test_). features/payment/paddle 참고 */
  readonly VITE_PADDLE_CLIENT_TOKEN?: string
  /** 'sandbox' | 'production'. 비우면 production */
  readonly VITE_PADDLE_ENVIRONMENT?: string
  /** 충전 횟수별 Paddle 가격 ID (pri_...). 백엔드 PADDLE_PRICE_ID_* 와 같은 값 */
  readonly VITE_PADDLE_PRICE_ID_1?: string
  readonly VITE_PADDLE_PRICE_ID_5?: string
  readonly VITE_PADDLE_PRICE_ID_12?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
