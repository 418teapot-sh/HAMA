import { formatWon, type CreditPlan } from './plans'

interface PlanOptionProps {
  plan: CreditPlan
  selected: boolean
  onSelect: () => void
}

/**
 * 요금제 카드 (Figma 컴포넌트: Credit/Plan/N/default · selected)
 * 너비 가득 / 높이 96 / 패딩 12·16 / 세로 간격 4 / 반경 20
 * - 기본: 배경 Card_Back · 테두리 1px Stroke · Card_Shadow · 배경 흐림 10
 * - 고름: 배경 Card_Back_2 40% · 테두리 1px Brand/Main_80 · 셋째 줄 "✓ 선택됨" Body/B9 Brand/Main_80
 * 첫 줄: 이름 Body/B3 Brand/Main · 금액 Body/B3 Black/Black (양 끝)
 *   (Figma 의 고른 카드 금액만 Noto Sans KR 700 이라, 다른 카드와 같은 Body/B3 로 맞춤 · 민서 확인)
 * 둘째 줄: 설명 · 단가 Body/B10 Black/Gray60 (양 끝)
 */
export default function PlanOption({ plan, selected, onSelect }: PlanOptionProps) {
  return (
    <button
      type="button"
      role="radio"
      aria-checked={selected}
      onClick={onSelect}
      className={`flex h-[96px] w-full flex-col items-start gap-[4px] rounded-[20px] border px-[16px] py-[12px] text-left ${
        selected
          ? 'border-brand-main-80 bg-card-back-2/40'
          : 'border-stroke bg-card-back shadow-card-shadow backdrop-blur-[10px]'
      }`}
    >
      <span className="flex w-full items-center justify-between gap-[4px] text-body-b3">
        <span className="text-brand-main">{plan.label}</span>
        <span className="text-black-black">{formatWon(plan.price)}</span>
      </span>
      <span className="flex w-full items-center justify-between gap-[4px] text-body-b10 text-black-gray60">
        <span>{plan.description}</span>
        <span>{plan.perUse}</span>
      </span>
      {selected && <span className="text-body-b9 text-brand-main-80">✓ 선택됨</span>}
    </button>
  )
}
