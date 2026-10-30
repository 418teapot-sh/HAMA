import { useState, type FormEvent } from 'react'
import { PlusIcon } from '../../components/TodoIcons'
import type { TodoCategory } from './categories'

export interface TodoInputValue {
  content: string
  /** 'HH:mm' (withTime 일 때만) */
  startTime?: string
  endTime?: string
}

interface TodoInputProps {
  category: TodoCategory
  /** 일정처럼 시작 · 종료 시간이 필요한 카테고리 */
  withTime?: boolean
  /** 안내 문구 (예: AI 목표 이름) */
  placeholder?: string
  /** 성공하면 true. 성공했을 때만 입력칸을 비웁니다 (실패 · 다른 요청 중이면 입력을 그대로 둠) */
  onSubmit: (value: TodoInputValue) => Promise<boolean>
}

/**
 * 투두 입력창 (Figma 투두리스트_추가 > Frame 2085668101)
 * 너비 358 / 높이 48 / 패딩 12·20 / 배경 White/W1 / 테두리 1px 카테고리 색 / 반경 8
 * 글자 Body/B6 Black/Gray80, 오른쪽 ic_add 24 카테고리 색
 *
 * 일정(withTime)은 시작 · 종료 시간을 받습니다.
 * TODO: 시간 입력 디자인은 디자이너 작업 중이라 지금은 기본 시간 입력칸을 입력창 아래에 둡니다. 디자인이 오면 바꿉니다.
 */
export default function TodoInput({ category, withTime = false, placeholder, onSubmit }: TodoInputProps) {
  const [content, setContent] = useState('')
  const [startTime, setStartTime] = useState('09:00')
  const [endTime, setEndTime] = useState('10:00')
  const [timeError, setTimeError] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const text = content.trim()
    if (!text) return
    if (withTime && !(startTime && endTime && startTime < endTime)) {
      setTimeError(true)
      return
    }
    setTimeError(false)
    const ok = await onSubmit(withTime ? { content: text, startTime, endTime } : { content: text })
    if (ok) setContent('')
  }

  return (
    <form onSubmit={(e) => void handleSubmit(e)} className="flex w-[358px] max-w-full flex-col gap-[8px]">
      <div
        className={`flex h-[48px] w-full items-start justify-between gap-[10px] rounded-[8px] border bg-white-w1 px-[20px] py-[12px] ${category.borderClass}`}
      >
        <input
          autoFocus
          value={content}
          placeholder={placeholder}
          aria-label={`${category.label} 내용`}
          onChange={(e) => setContent(e.target.value)}
          className="h-[24px] min-w-0 flex-1 text-body-b6 text-black-gray80 outline-none placeholder:text-black-gray40"
        />
        <button type="submit" aria-label={`${category.label} 추가하기`}>
          <PlusIcon className={`size-[24px] ${category.textClass}`} />
        </button>
      </div>
      {withTime && (
        <div className="flex w-full flex-col gap-[4px]">
          <div className="flex w-full items-center gap-[8px] text-body-b7 font-semibold text-black-gray80">
            <input
              type="time"
              value={startTime}
              aria-label="시작 시간"
              onChange={(e) => setStartTime(e.target.value)}
              className={`h-[36px] flex-1 rounded-[8px] border bg-white-w1 px-[12px] outline-none ${category.borderClass}`}
            />
            <span className="text-black-gray40">~</span>
            <input
              type="time"
              value={endTime}
              aria-label="종료 시간"
              onChange={(e) => setEndTime(e.target.value)}
              className={`h-[36px] flex-1 rounded-[8px] border bg-white-w1 px-[12px] outline-none ${category.borderClass}`}
            />
          </div>
          {timeError && <p className="text-[12px] leading-[16px] text-category-task">종료 시간은 시작 시간보다 늦어야 해요.</p>}
        </div>
      )}
    </form>
  )
}
