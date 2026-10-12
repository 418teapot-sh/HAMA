import type { ReactNode } from 'react'
import icPen from '../../../assets/icons/ic_pen.svg'
import icPenError from '../../../assets/icons/ic_pen_error.svg'
import icSparkle from '../../../assets/icons/ic_sparkle.svg'

/**
 * 목표 추가 단계 카드 (Figma: 새로운 AI 목표 > 목표 / 현재 상태 / 진행할 기간 카드)
 * - 카드: 패딩 20 / 반경 20 / 테두리 1 Stroke / 배경 Card_Back / Card_Shadow / 배경 흐림 10
 * - 제목(Heading/H6 Brand/Main_80) ↔ 내용 간격 12
 */
export function GoalStepCard({ label, children }: { label: string; children: ReactNode }) {
  return (
    <section className="flex w-full flex-col gap-[12px] rounded-[20px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]">
      <h2 className="text-heading-h6 text-brand-main-80">{label}</h2>
      {children}
    </section>
  )
}

/** 입력이 끝난 값: 반짝이 아이콘 24(Brand/Main_80) + 간격 8 + Body/B6 Brand/Main */
export function GoalStepValue({ children }: { children: ReactNode }) {
  return (
    <div className="flex items-start gap-[8px]">
      <img src={icSparkle} alt="" className="size-[24px] shrink-0" />
      <p className="pb-[4px] text-body-b6 text-brand-main">{children}</p>
    </div>
  )
}

type GoalStepInputProps = {
  value: string
  onChange: (value: string) => void
  placeholder: string
  /** 입력칸 앞 이름 (진행할 기간의 '시작일' · '종료일'). Body/B5 Regular Brand/Main_80 */
  prefix?: string
  inputMode?: 'text' | 'numeric'
  autoFocus?: boolean
  onBlur?: () => void
  /** 오류 안내 문구. 있으면 이름·연필·밑줄이 Brand/EO 색으로 바뀌고 아래에 안내가 보임 */
  error?: string
}

/**
 * 입력칸: [이름] + 연필 아이콘 24(Black/Gray40) + 밑줄 입력칸, 간격 8
 * - 입력칸: 아래 여백 4 / 아래 테두리 1 Black/Gray40 / Body/B6, 안내 글자 Black/Gray40
 * - 오류 (Figma: 새로운 AI 목표-입력 후 / 오류): 이름·연필·밑줄 Brand/EO,
 *   입력칸 아래 Body/B11 Brand/EO 안내
 */
export function GoalStepInput({
  value,
  onChange,
  placeholder,
  prefix,
  inputMode = 'text',
  autoFocus,
  onBlur,
  error,
}: GoalStepInputProps) {
  return (
    <label className="flex items-center gap-[8px]">
      {prefix && (
        <span className={`shrink-0 text-body-b5 font-normal ${error ? 'text-brand-eo' : 'text-brand-main-80'}`}>
          {prefix}
        </span>
      )}
      <img src={error ? icPenError : icPen} alt="" className="size-[24px] shrink-0" />
      <span className="flex min-w-0 flex-1 flex-col">
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onBlur={onBlur}
          placeholder={placeholder}
          inputMode={inputMode}
          autoFocus={autoFocus}
          aria-invalid={Boolean(error)}
          className={`w-full border-b bg-transparent pb-[4px] text-body-b6 text-brand-main outline-none placeholder:text-black-gray40 ${
            error ? 'border-brand-eo' : 'border-black-gray40'
          }`}
        />
        {error && <span className="text-body-b11 text-brand-eo">{error}</span>}
      </span>
    </label>
  )
}
