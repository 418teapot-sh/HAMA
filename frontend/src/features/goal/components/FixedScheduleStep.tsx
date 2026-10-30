import { useRef, useState, type KeyboardEvent } from 'react'
import icAdd from '../../../assets/icons/ic_add.svg'
import Checkbox from '../../../components/Checkbox'
import { WEEKDAYS, type Weekday } from '../weekdays'

type FixedScheduleStepProps = {
  /** 요일별 고정 일정 제목. 고른 요일만 키가 있음 */
  value: Partial<Record<Weekday, string[]>>
  onChange: (value: Partial<Record<Weekday, string[]>>) => void
}

/** 일정 제목 수정칸. Enter · 바깥 누르기로 저장, Esc 로 취소. 비우면 원래 제목을 유지 */
function ScheduleTitleInput({
  title,
  label,
  onSave,
  onCancel,
}: {
  title: string
  label: string
  onSave: (title: string) => void
  onCancel: () => void
}) {
  const [draft, setDraft] = useState(title)
  /** Enter · Esc 뒤 입력칸이 사라지며 생기는 blur 로 또 처리하지 않습니다 */
  const finished = useRef(false)

  const save = () => {
    if (finished.current) return
    finished.current = true
    const next = draft.trim()
    if (next) onSave(next)
    else onCancel()
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.nativeEvent.isComposing) return
    if (e.key === 'Enter') {
      e.preventDefault()
      save()
    } else if (e.key === 'Escape') {
      finished.current = true
      onCancel()
    }
  }

  return (
    <input
      type="text"
      value={draft}
      onChange={(e) => setDraft(e.target.value)}
      onKeyDown={handleKeyDown}
      onBlur={save}
      autoFocus
      aria-label={label}
      className="min-w-0 flex-1 bg-transparent text-black-black outline-none"
    />
  )
}

/** 요일 하나의 고정 일정 목록 + 새 일정 입력칸 (Enter 로 추가). 입력한 일정은 수정 · 삭제할 수 있음 */
function DaySchedules({
  day,
  titles,
  onChange,
}: {
  day: Weekday
  titles: string[]
  onChange: (titles: string[]) => void
}) {
  const [draft, setDraft] = useState('')
  /** 수정 중인 일정의 순서 */
  const [editingIndex, setEditingIndex] = useState<number | null>(null)

  const add = () => {
    const title = draft.trim()
    if (!title) return
    onChange([...titles, title])
    setDraft('')
  }

  const handleKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    // 한글 조합 중 Enter 는 무시 (마지막 글자가 두 번 들어가는 것 방지)
    if (e.key === 'Enter' && !e.nativeEvent.isComposing) {
      e.preventDefault()
      add()
    }
  }

  return (
    <div className="flex flex-col gap-[8px]">
      <p className="flex items-center gap-[8px] text-heading-h6 text-brand-main-80">
        <span className="flex size-[24px] items-center justify-center rounded-full bg-brand-main-20">
          <img src={icAdd} alt="" className="size-[20px]" />
        </span>
        {day}
      </p>
      <ul className="flex flex-col gap-[4px] pl-[8px]">
        {titles.map((title, index) => (
          <li key={`${title}-${index}`} className="flex items-center gap-[8px] text-body-b8 text-black-black">
            <span aria-hidden>•</span>
            {index === editingIndex ? (
              <ScheduleTitleInput
                title={title}
                label={`${title} 수정`}
                onSave={(next) => {
                  onChange(titles.map((item, i) => (i === index ? next : item)))
                  setEditingIndex(null)
                }}
                onCancel={() => setEditingIndex(null)}
              />
            ) : (
              <>
                <span className="flex-1">{title}</span>
                <button
                  type="button"
                  aria-label={`${title} 수정`}
                  className="text-body-b8 text-black-gray40"
                  onClick={() => setEditingIndex(index)}
                >
                  수정
                </button>
                <button
                  type="button"
                  aria-label={`${title} 삭제`}
                  className="text-body-b8 text-black-gray40"
                  onClick={() => onChange(titles.filter((_, i) => i !== index))}
                >
                  삭제
                </button>
              </>
            )}
          </li>
        ))}
        <li className="flex items-center gap-[8px] text-body-b8">
          <span aria-hidden className="text-black-gray40">
            •
          </span>
          <input
            type="text"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={handleKeyDown}
            onBlur={add}
            placeholder="고정일정을 입력해주세요."
            aria-label={`${day}요일 고정일정`}
            className="min-w-0 flex-1 bg-transparent text-black-black outline-none placeholder:text-black-gray40"
          />
        </li>
      </ul>
    </div>
  )
}

/**
 * 목표 추가 - 고정 일정 (Figma: 새로운 AI 목표-일정 입력 및 AI 진행 > 고정일정 입력)
 * - 요일 7개: 체크박스 24 + 요일 글자(고르면 Body/B7 SemiBold Black/Black, 아니면 Black/Gray40)
 * - 고른 요일마다: [+ 요일] 제목(Heading/H6 Brand/Main_80) + 일정 목록(Body/B8)
 *   카드 스타일은 목표 단계 카드와 같음
 * TODO: Figma 의 "고정일정 캘린더에 추가하기"는 일정 API 가 시작·종료 시간을 요구해서 보류 (디자인·백엔드 확인 필요)
 */
export default function FixedScheduleStep({ value, onChange }: FixedScheduleStepProps) {
  const selectedDays = WEEKDAYS.filter((day) => value[day])

  const toggleDay = (day: Weekday, checked: boolean) => {
    const next = { ...value }
    if (checked) next[day] = []
    else delete next[day]
    onChange(next)
  }

  return (
    <div className="flex flex-col gap-[16px]">
      <div className="flex justify-between">
        {WEEKDAYS.map((day) => {
          const checked = Boolean(value[day])
          return (
            <label key={day} className="flex flex-col items-center gap-[4px]">
              <Checkbox id={`fixed-day-${day}`} checked={checked} onChange={(next) => toggleDay(day, next)} />
              <span className={`text-body-b7 ${checked ? 'font-semibold text-black-black' : 'text-black-gray40'}`}>
                {day}
              </span>
            </label>
          )
        })}
      </div>

      {selectedDays.length > 0 && (
        <section className="flex flex-col gap-[16px] rounded-[20px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]">
          {selectedDays.map((day) => (
            <DaySchedules
              key={day}
              day={day}
              titles={value[day] ?? []}
              onChange={(titles) => onChange({ ...value, [day]: titles })}
            />
          ))}
        </section>
      )}
    </div>
  )
}
