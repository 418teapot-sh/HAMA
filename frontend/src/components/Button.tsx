import type { ButtonHTMLAttributes } from 'react'

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement>

/**
 * 공용 버튼 (Figma 컴포넌트: Button)
 * - 기본(Default): 배경 Brand/Main
 * - 비활성(Variant2): 배경 Black/Gray20  ← 회원가입 화면의 비활성 버튼에서 확인
 * 높이 48 / 반경 12 / 패딩 12·20 / 글자 Body/B3 White/W1
 *
 * TODO: #47 의 공용 Button 이 develop 에 머지되면 그걸로 교체합니다.
 */
export default function Button({ className = '', disabled, type = 'button', ...rest }: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled}
      className={`flex h-[48px] w-full items-center justify-center gap-[10px] rounded-[12px] px-[20px] py-[12px] text-body-b3 text-white-w1 ${
        disabled ? 'bg-black-gray20' : 'bg-brand-main'
      } ${className}`}
      {...rest}
    />
  )
}
