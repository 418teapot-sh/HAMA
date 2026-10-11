import type { ReactNode } from 'react'

interface InfoCardProps {
  title: string
  children: ReactNode
  /** 카드 맨 아래 굵은 한 줄 (예: 1회 차감) */
  note?: string
  /** false 면 배경 · 테두리 · 그림자 없음 (06 · 결제 관련 안내) */
  boxed?: boolean
  /** 모바일 본문 자간: 기본 -2%, 06 의 일부 답변은 -4% */
  tightBody?: boolean
  /** 데스크톱에서 같은 줄 카드끼리 너비를 나눠 가짐 (한 줄에 혼자 있는 카드는 false) */
  desktopGrow?: boolean
  className?: string
}

/**
 * 제목 + 설명 카드 (Figma 05 · 제공 혜택 / 06 · 결제 관련 안내의 카드)
 * 반경 20 / (boxed) 배경 Card_Back · 테두리 1px Stroke · Card_Shadow · 배경 흐림 10
 * 모바일: 패딩 20 / [제목 18px 900 + 설명 14px 400, 간격 8] 간격 13 [note 14px 700] / 행간 155%
 * 데스크톱: 패딩 40 / [제목 30px 800 행간 120% + 설명 20px 400 행간 140%, 간격 12] 간격 20 [note 20px 700 행간 120%]
 * 자간 모두 -2% / Black/Black
 */
export default function InfoCard({ title, children, note, boxed = true, tightBody, desktopGrow = true, className = '' }: InfoCardProps) {
  return (
    <div
      className={`flex w-full flex-col gap-[13px] rounded-[20px] p-[20px] desktop:gap-[20px] desktop:p-[40px] ${desktopGrow ? 'desktop:flex-1' : ''} ${
        boxed ? 'border border-stroke bg-card-back shadow-card-shadow backdrop-blur-[10px]' : ''
      } ${className}`}
    >
      <div className="flex w-full flex-col gap-[8px] desktop:gap-[12px]">
        <h3 className="text-[18px] leading-[28px] font-black tracking-[-0.02em] text-black-black desktop:text-[30px] desktop:leading-[36px] desktop:font-extrabold">
          {title}
        </h3>
        <p
          className={`text-[14px] leading-[22px] font-normal text-black-black desktop:text-[20px] desktop:leading-[28px] desktop:tracking-[-0.02em] ${
            tightBody ? 'tracking-[-0.04em]' : 'tracking-[-0.02em]'
          }`}
        >
          {children}
        </p>
      </div>
      {note && (
        <p className="text-[14px] leading-[22px] font-bold tracking-[-0.02em] text-black-black desktop:text-[20px] desktop:leading-[24px]">
          {note}
        </p>
      )}
    </div>
  )
}
