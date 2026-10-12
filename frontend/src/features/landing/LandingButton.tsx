import type { ButtonHTMLAttributes } from 'react'

/**
 * 랜딩 버튼 (Figma Button · 회원가입하고 시작하기)
 * 배경 linear-gradient(90.6deg, #0950BE -18.89%, #042558 129.62%) / 반경 12 / 글자 White/W1 가운데
 * 모바일: 높이 52 / 너비 가득 / 패딩 13·20 / 16px 700 행간 155%(25px)
 * 데스크톱: 높이 66 / 너비 276 고정 (회원가입하고 시작하기 · 결제하기 같은 너비, Figma 프로토 타입) / 패딩 20·40 / Heading/H3 22px 700 행간 26px
 * (10/12 Figma 수정으로 요금제 카드 안 충전 버튼이 없어져 이 모양 하나만 남았습니다)
 */
export default function LandingButton({ className = '', type = 'button', ...rest }: ButtonHTMLAttributes<HTMLButtonElement>) {
  return (
    <button
      type={type}
      className={`flex h-[52px] w-full shrink-0 items-center justify-center gap-[7px] rounded-[12px] bg-[linear-gradient(90.6deg,#0950be_-18.89%,#042558_129.62%)] px-[20px] py-[13px] text-center text-[16px] leading-[25px] font-bold text-white-w1 desktop:h-[66px] desktop:w-[276px] desktop:gap-[10px] desktop:px-[40px] desktop:py-[20px] desktop:text-[22px] desktop:leading-[26px] ${className}`}
      {...rest}
    />
  )
}
