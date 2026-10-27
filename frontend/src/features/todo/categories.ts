import type { CalendarCategory } from '../../components/Calendar'

/**
 * 투두 화면의 카테고리 4개
 * 순서: 할일 · 일정 · 기타 · AI (Figma 투두리스트_기본상태 카테고리 바, 디자이너 답변)
 * - color: Figma 색상 스타일 (할일 · 일정 · 기타 · AI 는 Brand/Main_40)
 * - AI 카테고리 = 사용자가 AI 목표에 직접 추가한 투두 (할일처럼 수정 · 삭제 · 날짜 변경 가능)
 *   AI 가 만든 투두는 카테고리는 AI 지만 색이 Brand/Main 이고 수정 · 삭제할 수 없습니다 (LocalTodo.aiGenerated)
 * - 키는 공용 캘린더 표시 종류(CalendarCategory)와 같습니다.
 * - 백엔드 연결: 할일 = TASK, AI = AI_GOAL_TASK(캘린더 AI_GOAL), 일정 = 고정·개인 일정(캘린더 FIXED · PERSONAL)
 *   TODO: 기타는 백엔드에 맞는 종류가 없어 연동 작업 때 확인 필요
 */
export type TodoCategoryKey = CalendarCategory

export interface TodoCategory {
  key: TodoCategoryKey
  label: string
  /** CSS 색 값 (캐릭터 SVG 에도 씀) */
  color: string
  /** 같은 색의 Tailwind 글자색 클래스 */
  textClass: string
  /** 같은 색의 Tailwind 테두리 클래스 */
  borderClass: string
  /** 같은 색의 Tailwind 배경 · 테두리 클래스 */
  fillClass: string
}

export const TODO_CATEGORIES: TodoCategory[] = [
  { key: 'task', label: '할일', color: 'var(--color-category-task)', textClass: 'text-category-task', borderClass: 'border-category-task', fillClass: 'bg-category-task border-category-task' },
  { key: 'schedule', label: '일정', color: 'var(--color-category-schedule)', textClass: 'text-category-schedule', borderClass: 'border-category-schedule', fillClass: 'bg-category-schedule border-category-schedule' },
  { key: 'etc', label: '기타', color: 'var(--color-category-etc)', textClass: 'text-category-etc', borderClass: 'border-category-etc', fillClass: 'bg-category-etc border-category-etc' },
  { key: 'ai', label: 'AI', color: 'var(--color-brand-main-40)', textClass: 'text-brand-main-40', borderClass: 'border-brand-main-40', fillClass: 'bg-brand-main-40 border-brand-main-40' },
]

/**
 * AI 가 만든 투두의 색 (Figma 투두리스트_기본상태: 캐릭터 · 체크박스 · 완료 글자 Brand/Main)
 * 카테고리 바에는 없고 목록에서만 씁니다.
 */
export const AI_GENERATED_STYLE: Pick<TodoCategory, 'color' | 'textClass'> = {
  color: 'var(--color-brand-main)',
  textClass: 'text-brand-main',
}

/**
 * 수정 화면에서 고를 수 있는 카테고리 (Figma 투두리스트_수정: 할일 · 일정 · 기타)
 * 다른 투두를 AI 투두로 바꾸는 것은 막습니다 (디자이너 답변).
 */
export const EDITABLE_CATEGORIES = TODO_CATEGORIES.filter((c) => c.key !== 'ai')

export function getCategory(key: TodoCategoryKey) {
  return TODO_CATEGORIES.find((c) => c.key === key)!
}
