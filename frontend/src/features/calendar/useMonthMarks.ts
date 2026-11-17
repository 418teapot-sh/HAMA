import { useEffect, useState } from 'react'
import type { CalendarMark } from '@/components/Calendar'
import { getMonthCalendar, toCalendarMarks } from './api'

/**
 * 그 달(month 는 1~12) 캘린더 뱃지를 불러옵니다. 홈 · 투두리스트 캘린더가 같이 씁니다.
 * reloadKey 가 바뀌면 다시 불러옵니다 (투두 추가 · 완료 · 이동 · 삭제 뒤).
 * 뱃지는 보조 정보라, 불러오기에 실패하면 이전 뱃지를 그대로 둡니다.
 */
export function useMonthMarks(year: number, month: number, reloadKey: number) {
  const [marks, setMarks] = useState<Record<string, CalendarMark[]>>({})

  useEffect(() => {
    let ignore = false
    getMonthCalendar(year, month)
      .then((items) => {
        if (!ignore) setMarks(toCalendarMarks(items))
      })
      .catch(() => {})
    return () => {
      ignore = true
    }
  }, [year, month, reloadKey])

  return marks
}
