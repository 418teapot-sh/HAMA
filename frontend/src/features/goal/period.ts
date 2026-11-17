import { toDateKey } from '@/utils/date'

/** 목표 기간 입력 ('YYYY.MM.DD') 검사 · 변환. 목표 추가 화면에서 씁니다. */

/** 'YYYY.MM.DD' 형식인지 */
const DATE_PATTERN = /^\d{4}\.\d{2}\.\d{2}$/

/** 목표 기간 최대 일수 (시작일 포함, 백엔드 기준) */
const MAX_PERIOD_DAYS = 365

/** 'YYYY.MM.DD' 형식이고 달력에 있는 날짜인지 (2월 30일 등을 걸러냄) */
export function isValidDate(value: string) {
  if (!DATE_PATTERN.test(value)) return false
  const [year, month, day] = value.split('.').map(Number)
  const date = new Date(year, month - 1, day)
  return date.getMonth() === month - 1 && date.getDate() === day
}

/** 'YYYY.MM.DD' → 'YYYY-MM-DD' (API 형식) */
export const toApiDate = (date: string) => date.replaceAll('.', '-')

/** 숫자만 남기고 'YYYY.MM.DD' 모양으로 점을 넣음. 예: '20260808' → '2026.08.08', '20261' → '2026.1' */
export function formatDateInput(value: string) {
  const digits = value.replace(/\D/g, '').slice(0, 8)
  return [digits.slice(0, 4), digits.slice(4, 6), digits.slice(6)].filter(Boolean).join('.')
}

/** 날짜 입력칸 오류 문구. 아직 건드리지 않았으면 표시하지 않음 */
export function dateError(label: string, value: string) {
  if (!value) return `${label}을 입력해주세요.`
  if (!isValidDate(value)) return '예: 2026.08.08 처럼 입력해주세요.'
  return undefined
}

/** 시작일 포함 며칠짜리 기간인지. 둘 다 'YYYY.MM.DD' */
function periodDays(startDate: string, endDate: string) {
  const toUtc = (value: string) => {
    const [year, month, day] = value.split('.').map(Number)
    return Date.UTC(year, month - 1, day)
  }
  return (toUtc(endDate) - toUtc(startDate)) / 86_400_000 + 1
}

/** 기간 오류 문구. 백엔드 GOAL_INVALID_PERIOD · GOAL_PERIOD_TOO_LONG 과 같은 기준 */
export function periodError(startDate: string, endDate: string) {
  if (endDate < startDate) return '종료일은 시작일 이후여야 해요.'
  if (toApiDate(endDate) < toDateKey(new Date())) return '종료일은 오늘 이후여야 해요.'
  if (periodDays(startDate, endDate) > MAX_PERIOD_DAYS) return '목표 기간은 최대 1년이에요.'
  return undefined
}
