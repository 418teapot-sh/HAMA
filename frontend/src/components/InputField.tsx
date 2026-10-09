import type { InputHTMLAttributes } from 'react'

interface InputFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string
  label: string
}

/**
 * 공용 입력창 (Figma 컴포넌트: InputField)
 * - 라벨: 18px / 400 / 140%, Black/Black
 * - 입력 박스: 반경 8 / 패딩 12·20 / 배경 White/W1 / 테두리 1px
 *   - 비어 있을 때 테두리 Black/Gray10, 값이 있을 때 Black/Gray40 (회원가입 입력 완료 화면에서 확인)
 * - 입력 글자 Body/B6 Black/Gray80, 플레이스홀더 Body/B6 Black/Gray40
 *
 * TODO: #47 의 공용 Input 이 develop 에 머지되면 그걸로 교체합니다.
 */
export default function InputField({ id, label, value, className = '', ...rest }: InputFieldProps) {
  const filled = String(value ?? '').length > 0

  return (
    <div className={`flex w-full flex-col gap-[4px] px-[20px] ${className}`}>
      <label htmlFor={id} className="text-label text-black-black">
        {label}
      </label>
      <div
        className={`flex items-center gap-[10px] rounded-[8px] border bg-white-w1 px-[20px] py-[12px] ${
          filled ? 'border-black-gray40' : 'border-black-gray10'
        }`}
      >
        <input
          id={id}
          value={value}
          className="w-full min-w-0 bg-transparent text-body-b6 text-black-gray80 outline-none placeholder:text-black-gray40"
          {...rest}
        />
      </div>
    </div>
  )
}
