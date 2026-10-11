import type { ButtonHTMLAttributes } from 'react'

interface LandingButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /**
   * gradient: 회원가입하고 시작하기 (linear-gradient 90.6deg #0950BE -18.89% → #042558 129.62%)
   * gray: 1회 충전하기 (Black/Gray60) / navy: 10회 충전하기 (Brand/Main)
   */
  variant: 'gradient' | 'gray' | 'navy'
  /** 데스크톱 크기: hero(01 · 시작하기) 또는 plan(요금제 카드) */
  desktopSize: 'hero' | 'plan'
}

const BG = {
  gradient: 'bg-[linear-gradient(90.6deg,#0950be_-18.89%,#042558_129.62%)]',
  gray: 'bg-black-gray60',
  navy: 'bg-brand-main',
}

/** 데스크톱 크기 (모바일은 모두 같은 크기) */
const DESKTOP = {
  // 너비 내용만큼 / 높이 66 / 패딩 20·40 / 글자 Heading/H3 22px 700 행간 26px
  hero: 'desktop:h-[66px] desktop:w-auto desktop:gap-[10px] desktop:px-[40px] desktop:py-[20px] desktop:text-[22px] desktop:leading-[26px]',
  // 너비 가득 / 높이 48 / 패딩 12·20 / 글자 Body/B3 18px 600 행간 140%(25px)
  plan: 'desktop:h-[48px] desktop:gap-[10px] desktop:py-[12px] desktop:text-[18px] desktop:leading-[25px] desktop:font-semibold',
}

/**
 * 랜딩 버튼 (Figma Button)
 * 모바일: 높이 52 / 너비 가득 / 패딩 13·20 / 반경 12 / 글자 16px 700 행간 155%(25px) 가운데 White/W1
 * TODO: 누르면 이동할 곳은 회의 후 연결 (지금은 모양만)
 */
export default function LandingButton({ variant, desktopSize, className = '', type = 'button', ...rest }: LandingButtonProps) {
  return (
    <button
      type={type}
      className={`flex h-[52px] w-full shrink-0 items-center justify-center gap-[7px] rounded-[12px] px-[20px] py-[13px] text-center text-[16px] leading-[25px] font-bold text-white-w1 ${BG[variant]} ${DESKTOP[desktopSize]} ${className}`}
      {...rest}
    />
  )
}
