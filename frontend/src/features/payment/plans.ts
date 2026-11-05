/**
 * 크레딧 충전 요금제 (Figma 결제 > 충전 · 5회 선택의 요금제 카드 3개, 랜딩 요금제와 같은 가격)
 * credits 는 실제로 충전되는 횟수입니다. 백엔드 paddle.credit-price-ids 의 키(1 · 5 · 12)와 같습니다.
 */
export interface CreditPlan {
  credits: 1 | 5 | 12
  /** 카드 제목 (예: 10회 + 보너스 2회) */
  label: string
  /** 달러 단위 금액 (#250: 해외원화결제 차단 카드 결제 거절로 원화 → 달러) */
  price: number
  description: string
  /** 카드 오른쪽 아래 단가 문구 */
  perUse: string
}

export const CREDIT_PLANS: CreditPlan[] = [
  { credits: 1, label: '1회', price: 1, description: '한 가지 목표에 집중할 때', perUse: '1회당 $1.00' },
  { credits: 5, label: '5회', price: 4, description: '여러 목표를 동시에', perUse: '1회당 $0.80' },
  { credits: 12, label: '10회 + 보너스 2회', price: 8, description: '더 많은 목표를 동시에', perUse: '총 12회 · 1회당 약 $0.67' },
]

/** 처음에 골라 둘 요금제: 추천 5회 (Figma 캡션 "기본 선택: 추천 5회") */
export const DEFAULT_PLAN = CREDIT_PLANS[1]

/** 4 → '$4' */
export function formatPrice(price: number) {
  return `$${price.toLocaleString('en-US')}`
}
