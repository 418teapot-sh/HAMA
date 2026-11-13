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

/** 한 번에 내보낼 수 있는 최대 기간 (백엔드: 양끝 포함 366일) */
const EXPORT_MAX_DAYS = 366

/**
 * 캘린더 .ics 내보내기 (GET /api/v1/calendar/export)
 * year · month(1~12) 달의 1일부터 1년치(366일)를 받아 휴대폰에 파일로 내려받게 합니다.
 * 받은 .ics 를 열면 휴대폰 · 구글 캘린더에 일정을 추가할 수 있습니다. 하마의 일정은 그대로 남습니다.
 */
export async function exportCalendar(year: number, month: number) {
  const fromDate = new Date(year, month - 1, 1)
  const toDate = new Date(year, month - 1, EXPORT_MAX_DAYS)
  const from = toDateKey(fromDate)
  const to = toDateKey(toDate)
  const res = await api.get<Blob>('/api/v1/calendar/export', {
    params: { from, to },
    responseType: 'blob',
    // 실패는 공통 JSON 으로 오므로 둘 다 받습니다. (Accept 가 맞지 않으면 406)
    headers: { Accept: 'text/calendar, application/json' },
  })
  downloadFile(res.data, `hama-${from}-${to}.ics`)
}

/** 받은 파일을 브라우저 다운로드로 저장합니다. */
function downloadFile(blob: Blob, fileName: string) {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  link.remove()
  // 바로 해제하면 일부 브라우저(사파리)에서 다운로드가 끊겨서 조금 뒤에 해제합니다.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
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
