import type { ReactNode } from 'react'

interface PlanCardProps {
  title: ReactNode
  /** 제목 오른쪽 뱃지 (예: 추천) */
  badge?: string
  description: string
  price: string
  perUse: string
  /** 빨간 절약 문구 (예: 1회보다 1,050원 절약) */
  saving?: string
  /** 카드 테두리 색 (모바일 · 데스크톱이 다를 수 있어 둘 다 적음) */
  borderClass: string
}

/**
 * 요금제 카드 (Figma 04 · 요금제 > Frame 2085668186 · 185 · 187)
 * 반경 20 / 배경 Card_Back / 테두리 1px / Card_Shadow / 배경 흐림 10
 * 10/12 Figma 수정: 카드 안 충전 버튼 없음 (시작하기 버튼은 카드 줄 아래 SignupCta)
 * 모바일: 패딩 20 / 세로 간격 13: [제목 · 설명 (간격 8)] [가격 · 1회당 · 절약 (간격 8)]
 *   제목 18px 900 · 설명 · 1회당 · 절약 14px 400 (행간 155%) / 가격 36px 900 (행간 140%)
 *   뱃지: 너비 52 · 글자 가운데 / 패딩 2·10 / 12px 700 행간 155%
 * 데스크톱: 패딩 40 / 카드 셋이 같은 높이(318) / 간격 20 · 12
 *   제목 30px 800 · 설명 · 1회당 · 절약 20px 400 · 가격 60px 800 (행간 모두 120%)
 *   뱃지: 높이 32 / 패딩 0·20 / 20px 600 행간 160%
 * 자간 모두 -2% / 뱃지 반경 100 Brand/Main White/W1
 */
export default function PlanCard({
  title,
  badge,
  description,
  price,
  perUse,
  saving,
  borderClass,
}: PlanCardProps) {
  return (
    <div
      className={`flex w-full flex-col gap-[13px] rounded-[20px] border bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px] desktop:flex-1 desktop:gap-[20px] desktop:p-[40px] ${borderClass}`}
    >
      <div className="flex w-full flex-col gap-[13px] desktop:gap-[20px]">
        <div className="flex w-full flex-col gap-[8px] desktop:gap-[12px]">
          <div className="flex w-full items-start gap-[12px] desktop:items-center desktop:gap-[8px]">
            <h3 className="flex-1 text-[18px] leading-[28px] font-black tracking-[-0.02em] text-black-black desktop:text-[30px] desktop:leading-[36px] desktop:font-extrabold">
              {title}
            </h3>
            {badge && (
              <span className="w-[52px] rounded-[100px] bg-brand-main px-[10px] text-center desktop:w-auto py-[2px] text-[12px] leading-[19px] font-bold text-white-w1 desktop:px-[20px] desktop:py-0 desktop:text-[20px] desktop:leading-[32px] desktop:font-semibold">
                {badge}
              </span>
            )}
          </div>
          <p className="text-[14px] leading-[22px] tracking-[-0.02em] text-black-black desktop:text-[20px] desktop:leading-[24px]">
            {description}
          </p>
        </div>
        <div className="flex w-full flex-col gap-[8px] desktop:gap-[12px]">
          <p className="text-[36px] leading-[50px] font-black tracking-[-0.02em] text-black-black desktop:text-[60px] desktop:leading-[72px] desktop:font-extrabold">
            {price}
          </p>
          <p className="text-[14px] leading-[22px] tracking-[-0.02em] text-black-black desktop:text-[20px] desktop:leading-[24px]">
            {perUse}
          </p>
          {saving && (
            <p className="text-[14px] leading-[22px] tracking-[-0.02em] text-category-task desktop:text-[20px] desktop:leading-[24px]">
              {saving}
            </p>
          )}
        </div>
      </div>
    </div>
  )
}
