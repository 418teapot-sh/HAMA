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
   * 시간 'HH:mm'. 있으면 체크박스와 내용 사이에 보여 줍니다 (Figma 투두리스트_기본상태 10/28 갱신본의 일정 '15:00').
   * TODO: Figma 에는 일정에만 시간이 있어, 일정을 연동할 때 채웁니다. 할일 · AI 투두에도 시간을 보여 줄지는 확인 필요
   */
  time?: string
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
