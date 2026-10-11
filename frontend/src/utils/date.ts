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


/** 'YYYY-MM-DD' 두 개 → '2026.08~2026.11 (3개월)'. 같은 달이면 '2026.10' */
export function formatGoalPeriod(startDate: string, endDate: string) {
  const [startYear, startMonth] = startDate.split('-').map(Number)
  const [endYear, endMonth] = endDate.split('-').map(Number)
  const months = (endYear - startYear) * 12 + (endMonth - startMonth)
  const pad = (month: number) => String(month).padStart(2, '0')
  const start = `${startYear}.${pad(startMonth)}`
  if (months <= 0) return start
  return `${start}~${endYear}.${pad(endMonth)} (${months}개월)`
}

const WEEKDAY_NAMES = ['일', '월', '화', '수', '목', '금', '토']

/** Date → '2026년 10월 2일 금' */
export function formatKoreanDate(date: Date) {
  return `${date.getFullYear()}년 ${date.getMonth() + 1}월 ${date.getDate()}일 ${WEEKDAY_NAMES[date.getDay()]}`
}
