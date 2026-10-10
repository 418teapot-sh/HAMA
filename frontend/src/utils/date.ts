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
