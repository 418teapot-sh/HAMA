import type { CreditPlan } from './plans'

/**
 * Paddle 결제창 (Paddle.js v2, Overlay checkout)
 *
 * 흐름 (백엔드 PaymentService 주석과 같음)
 *   프론트: Paddle.Checkout.open({ items: [{ priceId }], customData: { userId } })
 *   → 사용자 결제 → Paddle 이 백엔드 웹훅(transaction.completed) 호출 → 백엔드가 크레딧 충전
 *   그래서 결제창이 "완료"라고 알려 줘도 크레딧은 웹훅이 처리된 뒤에 늘어납니다.
 *
 * 필요한 환경변수 (.env.local / Vercel 프로젝트 설정)
 *   VITE_PADDLE_CLIENT_TOKEN   Paddle 대시보드 > Developer tools > Authentication 의 client-side token (live_ / test_)
 *   VITE_PADDLE_ENVIRONMENT    'sandbox' | 'production' (비우면 production)
 *   VITE_PADDLE_PRICE_ID_1 · _5 · _12   충전 횟수별 가격 ID (pri_...). 백엔드 PADDLE_PRICE_ID_* 와 같은 값
 */

/** 결제창이 알려 주는 결과 */
export type CheckoutOutcome = 'completed' | 'failed' | 'closed'

interface PaddleEvent {
  name?: string
}

interface PaddleGlobal {
  Environment: { set: (env: 'sandbox' | 'production') => void }
  Initialize: (options: { token: string; eventCallback?: (event: PaddleEvent) => void }) => void
  Checkout: {
    open: (options: {
      items: { priceId: string; quantity: number }[]
      customData?: Record<string, string>
      customer?: { email: string }
      settings?: { displayMode?: 'overlay'; locale?: string }
    }) => void
    close: () => void
  }
}

declare global {
  interface Window {
    Paddle?: PaddleGlobal
  }
}

const SCRIPT_URL = 'https://cdn.paddle.com/paddle/v2/paddle.js'

const PRICE_IDS: Record<CreditPlan['credits'], string | undefined> = {
  1: import.meta.env.VITE_PADDLE_PRICE_ID_1,
  5: import.meta.env.VITE_PADDLE_PRICE_ID_5,
  12: import.meta.env.VITE_PADDLE_PRICE_ID_12,
}

/** 결제 설정(토큰 · 가격 ID)이 다 들어 있는지. 없으면 결제창을 열 수 없습니다. */
export function isPaddleConfigured(plan: CreditPlan) {
  return !!import.meta.env.VITE_PADDLE_CLIENT_TOKEN && !!PRICE_IDS[plan.credits]
}

let loading: Promise<PaddleGlobal> | null = null
/** 지금 열린 결제창의 결과를 받을 곳 (Paddle 은 Initialize 때 콜백을 한 번만 받음) */
let listener: ((outcome: CheckoutOutcome) => void) | null = null
/** 지금 열린 결제창에서 결제 실패가 있었는지 */
let failedOnce = false

/**
 * 결제가 실패해도 사용자는 결제창 안에서 다시 시도할 수 있으므로, 실패는 기억만 해 두고
 * 결제창이 닫힐 때(실패했던 적이 있으면 'failed') 또는 결제가 끝났을 때 결과를 알립니다.
 */
function handleEvent(event: PaddleEvent) {
  if (!listener) return
  if (event.name === 'checkout.completed') listener('completed')
  else if (event.name === 'checkout.error' || event.name === 'checkout.payment.failed') failedOnce = true
  else if (event.name === 'checkout.closed') listener(failedOnce ? 'failed' : 'closed')
}

/** paddle.js 를 한 번만 불러와 초기화합니다. */
function loadPaddle() {
  loading ??= new Promise<PaddleGlobal>((resolve, reject) => {
    const init = () => {
      const paddle = window.Paddle
      if (!paddle) {
        reject(new Error('Paddle.js 를 불러오지 못했습니다.'))
        return
      }
      if (import.meta.env.VITE_PADDLE_ENVIRONMENT === 'sandbox') paddle.Environment.set('sandbox')
      paddle.Initialize({ token: import.meta.env.VITE_PADDLE_CLIENT_TOKEN ?? '', eventCallback: handleEvent })
      resolve(paddle)
    }
    if (window.Paddle) {
      init()
      return
    }
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = init
    script.onerror = () => reject(new Error('Paddle.js 를 불러오지 못했습니다.'))
    document.head.appendChild(script)
  }).catch((error: unknown) => {
    loading = null // 다음에 다시 시도할 수 있게
    throw error
  })
  return loading
}

/**
 * 결제창을 열고, 결제창이 닫히거나 결과가 나오면 끝납니다.
 * - 'completed': 결제 완료 (크레딧은 웹훅 처리 뒤 늘어남)
 * - 'failed': 결제가 실패한 채로 결제창을 닫음
 * - 'closed': 결제하지 않고 닫음
 * 결제 완료 뒤에도 Paddle 의 완료 화면이 떠 있으므로, completed 를 받으면 결제창을 닫습니다.
 */
export async function openCheckout(plan: CreditPlan, user: { id: number; email: string }) {
  const priceId = PRICE_IDS[plan.credits]
  if (!priceId) throw new Error(`${plan.credits}회 가격 ID(VITE_PADDLE_PRICE_ID_${plan.credits})가 없습니다.`)
  const paddle = await loadPaddle()
  return new Promise<CheckoutOutcome>((resolve) => {
    failedOnce = false
    listener = (outcome) => {
      listener = null
      if (outcome === 'completed') paddle.Checkout.close()
      resolve(outcome)
    }
    paddle.Checkout.open({
      items: [{ priceId, quantity: 1 }],
      // 백엔드 웹훅이 custom_data.userId 로 누구의 결제인지 찾습니다.
      customData: { userId: String(user.id) },
      customer: { email: user.email },
      settings: { displayMode: 'overlay', locale: 'ko' },
    })
  })
}
