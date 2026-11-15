/** Date → 'YYYY-MM-DD' (한국 시간 기준 날짜. toISOString 은 UTC 라서 하루 밀릴 수 있어 쓰지 않음) */
export function toDateKey(date: Date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** 오늘 0시 0분 */
export function startOfToday() {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return today
}


/**
 * 'YYYY-MM-DD' 두 개 → '2026.11~2026.12 (2개월)'. 같은 달이면 '2026.11 (1개월)'
 * 기간은 시작일 · 종료일을 포함한 실제 일 수로 계산합니다 (월 번호만 빼면 11.03~12.31 이 1개월이 되던 문제).
 * 30일 미만은 'N일', 그 이상은 30일 단위로 반올림한 'N개월'
 */
export function formatGoalPeriod(startDate: string, endDate: string) {
  const [startYear, startMonth, startDay] = startDate.split('-').map(Number)
  const [endYear, endMonth, endDay] = endDate.split('-').map(Number)
  const days = Math.round((Date.UTC(endYear, endMonth - 1, endDay) - Date.UTC(startYear, startMonth - 1, startDay)) / 86_400_000) + 1
  const pad = (month: number) => String(month).padStart(2, '0')
  const start = `${startYear}.${pad(startMonth)}`
  const range = startYear === endYear && startMonth === endMonth ? start : `${start}~${endYear}.${pad(endMonth)}`
  if (days <= 0) return range
  const length = days < 30 ? `${days}일` : `${Math.round(days / 30)}개월`
  return `${range} (${length})`
}

const WEEKDAY_NAMES = ['일', '월', '화', '수', '목', '금', '토']

/** Date → '2026년 10월 2일 금' */
export function formatKoreanDate(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAY_NAMES[date.getDay()]}`
}
