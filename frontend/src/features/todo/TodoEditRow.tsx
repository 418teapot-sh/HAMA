import { useState, type FormEvent } from 'react'
import Character from '../../components/Character'
import { CheckIcon } from '../../components/TodoIcons'
import { EDITABLE_CATEGORIES, getCategory, type TodoCategoryKey } from './categories'

export interface TodoEditValue {
  title: string
  category: TodoCategoryKey
  /** 일정으로 바꿀 때 'HH:mm' */
  startTime?: string
  endTime?: string
}

interface TodoEditRowProps {
  title: string
  category: TodoCategoryKey
  /** false 면 캐릭터(분류)를 고를 수 없음 (AI 목표 투두: 서버가 분류 변경을 막음) */
  allowCategory?: boolean
  onSave: (value: TodoEditValue) => void
}

/**
 * 투두 수정 줄 (Figma 투두리스트_수정 > Frame 2085668202) — 목록에서 그 투두 줄 자리에 들어감
 * 세로 간격 8:
 * - 캐릭터 고르기 (가로 간격 4, 순서 할일 · 일정 · 기타): 고른 카테고리 = 몸통 80% + 안쪽 테두리 1px, 나머지 = 몸통 20%
 *   AI 는 고를 수 없음 (다른 투두를 AI 투두로 바꾸지 못하게, 디자이너 답변).
 *   AI 목표에 추가한 투두는 분류를 바꿀 수 없어 캐릭터 줄을 숨깁니다.
 * - 입력 줄 (가로 간격 8, 높이 24): [입력칸 높이 22 · 아래 테두리 1px Black/Gray40 · 글자 Body/B7 SemiBold Black/Gray80] [ic_check 24 Black/Black]
 * - 할일 · 기타를 일정으로 바꾸면 시간이 필요해 시간 입력칸을 보여 줍니다 (TODO: 시간 입력 디자인 받으면 교체)
 * 입력칸은 원래 내용이 채워진 채로 시작합니다 (디자이너 답변).
 */
export default function TodoEditRow({ title, category, allowCategory = true, onSave }: TodoEditRowProps) {
  const [content, setContent] = useState(title)
  const [picked, setPicked] = useState(category)
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('10:00')
  const [timeError, setTimeError] = useState(false)
  const needsTime = picked === 'schedule' && category !== 'schedule'

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const text = content.trim()
    if (!text) return
    if (needsTime) {
      if (!(startTime < endTime)) {
        setTimeError(true)
        return
      }
      onSave({ title: text, category: picked, startTime, endTime })
      return
    }
    onSave({ title: text, category: picked })
  }

  const pickedStyle = getCategory(picked)

  return (
    <li className="flex w-full flex-col justify-center gap-[8px]">
      {allowCategory && (
        <div className="flex h-[36px] items-center gap-[4px]">
          {EDITABLE_CATEGORIES.map((item) => (
            <button
              key={item.key}
              type="button"
              aria-label={item.label}
              aria-pressed={picked === item.key}
              onClick={() => setPicked(item.key)}
            >
              <Character color={item.color} variant={picked === item.key ? 'picked' : 'faded'} />
            </button>
          ))}
        </div>
      )}
      <form onSubmit={handleSubmit} className="flex w-full flex-col gap-[8px]">
        <div className="flex h-[24px] w-full items-start gap-[8px]">
          <input
            autoFocus
            value={content}
            aria-label="투두 내용 수정"
            onChange={(e) => setContent(e.target.value)}
            className="h-[22px] min-w-0 flex-1 border-b border-black-gray40 text-body-b7 font-semibold text-black-gray80 outline-none"
          />
          <button type="submit" aria-label="수정 완료" className="shrink-0">
            <CheckIcon className="size-[24px] text-black-black" />
          </button>
        </div>
        {needsTime && (
          <div className="flex w-full flex-col gap-[4px]">
            <div className="flex w-full items-center gap-[8px] text-body-b7 font-semibold text-black-gray80">
              <input
                type="time"
                value={startTime}
                aria-label="시작 시간"
                onChange={(e) => setStartTime(e.target.value)}
                className={`h-[32px] flex-1 rounded-[8px] border bg-white-w1 px-[12px] outline-none ${pickedStyle.borderClass}`}
              />
              <span className="text-black-gray40">~</span>
              <input
                type="time"
                value={endTime}
                aria-label="종료 시간"
                onChange={(e) => setEndTime(e.target.value)}
                className={`h-[32px] flex-1 rounded-[8px] border bg-white-w1 px-[12px] outline-none ${pickedStyle.borderClass}`}
              />
            </div>
            {timeError && <p className="text-[12px] leading-[16px] text-category-task">종료 시간은 시작 시간보다 늦어야 해요.</p>}
          </div>
        )}
      </form>
    </li>
  )
}
