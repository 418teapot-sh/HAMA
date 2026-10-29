import type { TodoCategoryKey } from './categories'

/** 화면에 그리는 투두 한 줄 (백엔드 응답을 features/todo/api 의 toTodoViews 로 바꾼 것) */
export interface TodoView {
  id: number
  /** YYYY-MM-DD (utils/date 의 toDateKey) */
  date: string
  title: string
  category: TodoCategoryKey
  done: boolean
  /**
   * AI 가 만든 투두 (category 는 'ai'). 수정 · 삭제 · 날짜 변경을 할 수 없어 더보기(…) 대신 sparkle 을 보여 줍니다.
   * TODO: 백엔드 응답에 'AI 가 만든 투두'와 '사용자가 AI 목표에 추가한 투두'를 나누는 값이 없어,
   *       지금은 AI 목표 투두(AI_GOAL_TASK)를 모두 AI 가 만든 투두로 봅니다. 구분 값이 생기면 바꿉니다.
   */
  aiGenerated?: boolean
  /**
   * AI 투두: 완료한 뒤 상태 입력까지 했는지 (완료 투두의 statusNote 가 있으면 true).
   * 상태 입력 팝업은 AI 목표 흐름 쪽 작업이라, 지금은 아이콘 표시에만 씁니다.
   */
  aiStateEntered?: boolean
}
