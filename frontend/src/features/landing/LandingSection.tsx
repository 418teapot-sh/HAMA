import type { ReactNode } from 'react'

const PADDING_Y = { 64: 'py-[64px]', 48: 'py-[48px]' }

interface LandingSectionProps {
  children: ReactNode
  /** 모바일 위아래 패딩: 01 · 시작하기 64 / 나머지 48 */
  paddingY?: 64 | 48
  /** 데스크톱에서 높이 982 (01 · 04 · 06 · 내용은 세로 가운데) */
  tall?: boolean
  className?: string
}

/**
 * 랜딩 섹션 (Figma 온보딩 > HAMA · 모바일 웹 · 390 / MacBook Pro 14" - 1 의 각 섹션)
 * 공통: 배경 Card_Back / Card_Shadow / 배경 흐림 10 / 너비 화면 가득
 * 모바일: 패딩 위아래 48(01 은 64) · 좌우 20 / 테두리 1px Stroke
 *   Figma 에서 테두리가 높이에는 들어가고(예: 01 높이 460 = 64+330+64+2) 안쪽 너비는 350(=390-40)이라,
 *   테두리는 ring-inset 으로 그리고 위아래에 투명한 1px 를 두어 높이를 맞춥니다.
 * 데스크톱: 패딩 100 / 테두리 없음 / 안쪽 너비 최대 1312(=1512-200) 가운데
 * 글자 행간은 Figma 글자 상자 높이(반올림)대로 px 로 적습니다. 예) 16px · 155% → 25px
 */
export default function LandingSection({ children, paddingY = 48, tall, className = '' }: LandingSectionProps) {
  return (
    <section
      className={`flex w-full flex-col items-center border-y border-transparent bg-card-back px-[20px] shadow-card-shadow ring-1 ring-stroke ring-inset backdrop-blur-[10px] ${PADDING_Y[paddingY]} desktop:border-y-0 desktop:p-[100px] desktop:ring-0 ${
        tall ? 'desktop:min-h-[982px] desktop:justify-center' : ''
      }`}
    >
      <div className={`flex w-full flex-col desktop:max-w-[1312px] ${className}`}>{children}</div>
    </section>
  )
}

interface SectionHeaderProps {
  title: string
  /** 모바일 제목 크기: 기본 28, 05 · 제공 혜택만 25 */
  small?: boolean
  subtitle?: ReactNode
  /** 모바일 제목 · 설명 사이 간격: 02 · 04 는 10, 03 은 18 (데스크톱은 16) */
  gap?: 10 | 18
  /** 설명 굵기: 02 · 03 은 500, 04 는 400 */
  subtitleWeight?: 400 | 500
  /** 데스크톱 설명 행간: 한 줄(02) 120% → 34px, 두 줄(03 · 04) 150% → 42px */
  desktopSubtitleLeading?: 34 | 42
  /** 데스크톱에서 왼쪽 정렬 (03 · 이용화면 미리보기) */
  desktopLeft?: boolean
}

/**
 * 섹션 제목 + 설명
 * 모바일 제목: 28px(또는 25px) / 900 / 행간 140%(39 · 35px) / 자간 -2% / 가운데 / Black/Black
 * 모바일 설명: 16px / 400·500 / 행간 155%(25px) / 자간 -2%
 * 데스크톱 제목: 56px / 800 / 행간 150%(84px) · 설명: 28px / 행간 120%(34px) 또는 150%(42px)
 */
export function SectionHeader({
  title,
  small,
  subtitle,
  gap = 10,
  subtitleWeight = 500,
  desktopSubtitleLeading = 42,
  desktopLeft,
}: SectionHeaderProps) {
  const align = desktopLeft ? 'text-center desktop:text-left' : 'text-center'
  return (
    <div
      className={`flex w-full flex-col items-center desktop:gap-[16px] ${gap === 18 ? 'gap-[18px] desktop:gap-[28px]' : 'gap-[10px]'} ${
        desktopLeft ? 'desktop:items-start' : ''
      }`}
    >
      <h2
        className={`w-full font-black tracking-[-0.02em] text-black-black desktop:text-[56px] desktop:leading-[84px] desktop:font-extrabold ${align} ${
          small ? 'text-[25px] leading-[35px]' : 'text-[28px] leading-[39px]'
        }`}
      >
        {title}
      </h2>
      {subtitle && (
        <p
          className={`w-full text-[16px] leading-[25px] tracking-[-0.02em] text-black-black desktop:text-[28px] ${align} ${
            subtitleWeight === 500 ? 'font-medium' : 'font-normal'
          } ${desktopSubtitleLeading === 34 ? 'desktop:leading-[34px]' : 'desktop:leading-[42px]'}`}
        >
          {subtitle}
        </p>
      )}
    </div>
  )
}
