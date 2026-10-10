import { useState } from 'react'
import { startOfToday, toDateKey } from '../utils/date'

export type CalendarCategory = 'task' | 'schedule' | 'ai' | 'etc'

export type CalendarMark = {
  id: string | number
  title: string
  category: CalendarCategory
}

type CalendarProps = {
  selectedDate: Date
  onSelectDate: (date: Date) => void
  /** 날짜별 표시. 키는 'YYYY-MM-DD' (utils/date 의 toDateKey 로 만들기) */
  marks?: Record<string, CalendarMark[]>
  /** 보이는 달이 바뀔 때 (그 달 데이터 다시 불러오기용) */
  onMonthChange?: (year: number, month: number) => void
}

const WEEKDAYS = ['일', '월', '화', '수', '목', '금', '토']
const MAX_MARKS = 3

const MARK_COLOR: Record<CalendarCategory, string> = {
  task: 'bg-category-task',
  schedule: 'bg-category-schedule',
  ai: 'bg-brand-main',
  etc: 'bg-category-etc',
}

function isSameDay(a: Date, b: Date) {
  return toDateKey(a) === toDateKey(b)
}

/** 그 달 달력 칸 목록. 1일 앞의 빈칸은 null */
function getMonthCells(year: number, month: number) {
  const firstWeekday = new Date(year, month, 1).getDay() // 0=일 ~ 6=토
  const lastDate = new Date(year, month + 1, 0).getDate() // 다음 달 0일 = 이번 달 마지막 날
  const cells: (Date | null)[] = Array(firstWeekday).fill(null)
  for (let day = 1; day <= lastDate; day++) {
    cells.push(new Date(year, month, day))
  }
  return cells
}

function ChevronIcon({ direction }: { direction: 'left' | 'right' }) {
  return (
    <svg
      width="8"
      height="13"
      viewBox="0 0 8 13"
      fill="none"
      aria-hidden="true"
      className={direction === 'right' ? '-scale-x-100' : ''}
    >
      <path
        d="M7.65578 12.6601C8.11439 12.2072 8.11479 11.473 7.65668 11.0196L3.17313 6.58218C3.12724 6.53676 3.12724 6.46324 3.17313 6.41782L7.65668 1.98041C8.11479 1.52702 8.11439 0.792841 7.65578 0.339939C7.19681 -0.113313 6.45268 -0.113313 5.99372 0.339939L0.172532 6.08866C-0.0575104 6.31584 -0.0575104 6.68417 0.172532 6.91134L5.99372 12.6601C6.45268 13.1133 7.19681 13.1133 7.65578 12.6601Z"
        fill="currentColor"
      />
    </svg>
  )
}

/**
 * 공용 캘린더 (Figma 컴포넌트: Calendar, 홈 화면에서 확인)
 * - 카드: 패딩 20 / 반경 20 / 테두리 1 Stroke / 배경 Card_Back / 그림자 Card_Shadow / 배경 흐림 10
 * - 헤더(년·월 + 화살표) ↔ 달력 간격 20, 요일 줄 ↔ 날짜 간격 16
 * - 년·월: Body/B5 Black/Black, 화살표: 8×13 Black/Gray60
 * - 요일: 32×28, Body/B8 Black/Gray60
 * - 날짜 격자: 7열, 가로 간격 8 / 세로 간격 4, 칸(DayCell) 32×60
 * - 날짜 숫자: 21×21 원 안에 Body/B8. 기본 Black/Black, 지난 날짜 Black/Gray40,
 *   선택한 날짜는 원 배경 Brand/Main + 글자 White/W1
 * - 표시(뱃지): 숫자 아래 8 간격, 32×10 / 반경 100 / 카테고리 색 / 글자 8px White/W1, 최대 3개
 */
export default function Calendar({ selectedDate, onSelectDate, marks = {}, onMonthChange }: CalendarProps) {
  const [today] = useState(startOfToday)
  const [viewMonth, setViewMonth] = useState(
    () => new Date(selectedDate.getFullYear(), selectedDate.getMonth(), 1),
  )

  const year = viewMonth.getFullYear()
  const month = viewMonth.getMonth()
  const cells = getMonthCells(year, month)

  const moveMonth = (diff: number) => {
    const next = new Date(year, month + diff, 1)
    setViewMonth(next)
    onMonthChange?.(next.getFullYear(), next.getMonth() + 1)
  }

  return (
    <section className="flex w-full flex-col items-center gap-[20px] rounded-[20px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]">
      <div className="flex w-full items-center justify-between">
        <p className="text-body-b5 text-black-black">
          {year}년 {month + 1}월
        </p>
        <div className="flex items-center gap-[4px] text-black-gray60">
          <button
            type="button"
            aria-label="이전 달"
            className="flex size-[24px] items-center justify-center"
            onClick={() => moveMonth(-1)}
          >
            <ChevronIcon direction="left" />
          </button>
          <button
            type="button"
            aria-label="다음 달"
            className="flex size-[24px] items-center justify-center"
            onClick={() => moveMonth(1)}
          >
            <ChevronIcon direction="right" />
          </button>
        </div>
      </div>

      <div className="flex flex-col items-center gap-[16px]">
        <div className="grid grid-cols-7 gap-x-[8px]">
          {WEEKDAYS.map((weekday) => (
            <span
              key={weekday}
              className="flex h-[28px] w-[32px] items-center justify-center text-body-b8 text-black-gray60"
            >
              {weekday}
            </span>
          ))}
        </div>

        <div className="grid grid-cols-7 gap-x-[8px] gap-y-[4px]">
          {cells.map((date, index) => {
            if (!date) return <span key={`empty-${index}`} className="h-[60px] w-[32px]" />

            const key = toDateKey(date)
            const isSelected = isSameDay(date, selectedDate)
            const isPast = date < today
            const dayMarks = marks[key] ?? []

            return (
              <button
                key={key}
                type="button"
                aria-label={`${date.getMonth() + 1}월 ${date.getDate()}일`}
                aria-pressed={isSelected}
                className="flex h-[60px] w-[32px] flex-col items-center gap-[8px]"
                onClick={() => onSelectDate(date)}
              >
                <span
                  className={`flex size-[21px] items-center justify-center rounded-full text-body-b8 ${
                    isSelected ? 'bg-brand-main text-white-w1' : isPast ? 'text-black-gray40' : 'text-black-black'
                  }`}
                >
                  {date.getDate()}
                </span>

                <span className="flex w-full flex-col">
                  {dayMarks.slice(0, MAX_MARKS).map((mark) => (
                    <span
                      key={mark.id}
                      className={`flex h-[10px] w-full items-center justify-center overflow-hidden rounded-full whitespace-nowrap text-[8px] leading-[1.3] text-white-w1 ${MARK_COLOR[mark.category]}`}
                    >
                      {mark.title}
                    </span>
                  ))}
                </span>
              </button>
            )
          })}
        </div>
      </div>
    </section>
  )
}
