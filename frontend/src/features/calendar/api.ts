import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { CalendarCategory, CalendarMark } from '../../components/Calendar'
import { toDateKey } from '../../utils/date'

/** FIXED·PERSONAL = 고정·개인 일정, AI_GOAL = AI 목표 투두, TASK = 일반 할일, OTHER = 기타 (#223) */
export type CalendarItemType = 'FIXED' | 'PERSONAL' | 'AI_GOAL' | 'TASK' | 'OTHER'

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
  /** 투두 · 일정 모두 PENDING / COMPLETED (#236 부터 일정도 날짜별 완료) */
  status: string | null
  /** 일정: 이 반복 발생의 시작 일시 'yyyy-MM-ddTHH:mm:ss' (완료 · 완료 취소 요청에 그대로 보냄). 투두는 null */
  occurrenceStartAt?: string | null
}

const CATEGORY: Record<CalendarItemType, CalendarCategory> = {
  TASK: 'task',
  FIXED: 'schedule',
  PERSONAL: 'schedule',
  AI_GOAL: 'ai',
  OTHER: 'etc',
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
      // 모르는 종류가 와도 화면이 깨지지 않게 할일로 보여 줍니다.
      category: CATEGORY[item.type] ?? 'task',
    })
  }
  return marks
}
