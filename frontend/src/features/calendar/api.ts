import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { CalendarCategory, CalendarMark } from '../../components/Calendar'
import { toDateKey } from '../../utils/date'

/** FIXED·PERSONAL = 고정·개인 일정, AI_GOAL = AI 목표 투두, TASK = 일반 할일 */
export type CalendarItemType = 'FIXED' | 'PERSONAL' | 'AI_GOAL' | 'TASK'

/** 캘린더 항목 (GET /api/v1/calendar). 반복·여러 날 일정은 날짜별로 나눠서 옴 */
export interface CalendarItem {
  type: CalendarItemType
  refId: number
  title: string
  /** 'YYYY-MM-DD' */
  date: string
  /** 'HH:mm', 종일이면 null */
  startTime: string | null
  endTime: string | null
  allDay: boolean
  goalId: number | null
  status: string | null
}

const CATEGORY: Record<CalendarItemType, CalendarCategory> = {
  TASK: 'task',
  FIXED: 'schedule',
  PERSONAL: 'schedule',
  AI_GOAL: 'ai',
}

/** 그 달(1일~말일) 캘린더 항목. month 는 1~12 */
export async function getMonthCalendar(year: number, month: number) {
  const from = toDateKey(new Date(year, month - 1, 1))
  const to = toDateKey(new Date(year, month, 0))
  const res = await api.get<ApiResponse<{ items: CalendarItem[] }>>('/api/v1/calendar', {
    params: { from, to },
  })
  return res.data.data.items
}

/** 캘린더 항목 → Calendar 의 marks ({ 'YYYY-MM-DD': 뱃지[] }) */
export function toCalendarMarks(items: CalendarItem[]) {
  const marks: Record<string, CalendarMark[]> = {}
  for (const item of items) {
    marks[item.date] ??= []
    marks[item.date].push({
      id: `${item.type}-${item.refId}`,
      title: item.title,
      category: CATEGORY[item.type],
    })
  }
  return marks
}
