import type { InputHTMLAttributes, ReactNode } from 'react'

interface InputFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  id: string
  label: string
  /** 오류 문구. 있으면 테두리가 Brand/EO 로 바뀌고 박스 아래에 문구가 나옵니다. */
  error?: string
  /** 입력 박스 오른쪽 요소 (예: 비밀번호 보이기 버튼) */
  rightSlot?: ReactNode
}

/**
 * 공용 입력창 (Figma 컴포넌트: InputField)
 * - 세로 간격 4 / 좌우 패딩 20
 * - 라벨: Body/B4 (18/400/140%) Black/Black
 * - 입력 박스: 반경 8 / 패딩 12·20 / 간격 10 / 배경 White/W1 / 테두리 1px(안쪽, 크기에 포함), 양쪽 정렬 space-between
 *   - 기본 Black/Gray10, 값이 있을 때 Black/Gray40(회원가입 입력 완료 화면), 오류일 때 Brand/EO(이메일 오류 화면)
 * - 입력 글자 Body/B6 Black/Gray80, 플레이스홀더 Body/B6 Black/Gray40
 * - 오류 문구: Caption/Cap3 (12/400/130%) Brand/EO, 박스와 간격 4
 *
 * TODO: #47 의 공용 Input 이 develop 에 머지되면 그걸로 교체합니다.
 */
export default function InputField({ id, label, value, error, rightSlot, className = '', ...rest }: InputFieldProps) {
  const filled = String(value ?? '').length > 0
  const borderColor = error ? 'ring-brand-eo' : filled ? 'ring-black-gray40' : 'ring-black-gray10'
  const errorId = `${id}-error`

  return (
    <div className={`flex w-full flex-col gap-[4px] px-[20px] ${className}`}>
      <label htmlFor={id} className="text-body-b4 text-black-black">
        {label}
      </label>
      <div className="flex w-full flex-col gap-[4px]">
        <div
          className={`flex items-center justify-between gap-[10px] rounded-[8px] bg-white-w1 px-[20px] py-[12px] ring-1 ring-inset ${borderColor}`}
        >
          <input
            id={id}
            value={value}
            aria-invalid={error ? true : undefined}
            aria-describedby={error ? errorId : undefined}
            className="w-full min-w-0 bg-transparent text-body-b6 text-black-gray80 outline-none placeholder:text-black-gray40"
            {...rest}
          />
          {rightSlot}
        </div>
        {error && (
          <p id={errorId} className="text-caption-cap3 text-brand-eo">
            {error}
          </p>
        )}
      </div>
    </div>
  )
}
