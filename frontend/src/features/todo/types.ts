import type { TodoCategoryKey } from './categories'

/** 백엔드 투두 종류 (TASK 할일 · OTHER 기타 · AI_GOAL_TASK 목표 투두) */
export type TodoApiCategory = 'TASK' | 'OTHER' | 'AI_GOAL_TASK'

/**
 * 화면에 그리는 투두 한 줄 (features/todo/api 의 getDailyTodos 가 만듦)
 * - kind 'todo': 투두 API(/api/v1/todos)의 할일 · 기타 · AI 목표 투두
 * - kind 'schedule': 캘린더 API 의 개인(PERSONAL) · 고정(FIXED) 일정. 날짜(반복 발생)별로 완료 체크 (#236)
 * 투두 ID 와 일정 ID 는 숫자가 겹칠 수 있어, 화면에서는 key 로 구분합니다.
 */
export interface TodoView {
  /** `${kind}-${id}` */
  key: string
  kind: 'todo' | 'schedule'
  id: number
  /** YYYY-MM-DD (utils/date 의 toDateKey) */
  date: string
  title: string
  category: TodoCategoryKey
  /** 투두일 때 백엔드 종류 */
  apiCategory?: TodoApiCategory
  /** 일정일 때 종류 (고정 일정은 목표 만들 때 넣은 것이라 투두 화면에서 바꾸지 않습니다) */
  scheduleType?: 'PERSONAL' | 'FIXED'
  /** 일정: 이 반복 발생의 시작 일시. 완료 · 완료 취소 요청에 date 와 함께 보냅니다 (#236) */
  occurrenceStartAt?: string | null
  done: boolean
  /** 시간 'HH:mm'. 있으면 체크박스와 내용 사이에 보여 줍니다 (Figma 일정 '15:00') */
  time?: string
  /** 목표 투두의 목표 ID */
  goalId?: number | null
  /**
   * AI 플랜으로 만든 투두 (서버 aiGenerated === true).
   * 내용 · 시간 수정과 삭제를 할 수 없어 더보기(…) 대신 sparkle 을 보여 줍니다.
   * 사용자가 AI 목표에 직접 추가한 투두(aiGenerated false)는 AI 색이지만 할일처럼 수정 · 삭제할 수 있습니다.
   */
  aiGenerated?: boolean
  /** 목표 투두의 현재 상태 메모 (PATCH /api/v1/todos/state/{id}) */
  statusNote?: string | null
}
