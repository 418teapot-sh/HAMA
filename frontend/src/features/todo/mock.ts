import type { TodoCategoryKey } from './categories'

/** 화면 안에서만 쓰는 투두 (백엔드 연동 전) */
export interface LocalTodo {
  id: number
  /** YYYY-MM-DD (utils/date 의 toDateKey) */
  date: string
  title: string
  category: TodoCategoryKey
  done: boolean
  /**
   * AI 가 만든 투두 (category 는 'ai'). 수정 · 삭제 · 날짜 변경을 할 수 없어 더보기(…) 대신 sparkle 을 보여 줍니다.
   * 사용자가 AI 목표에 직접 추가한 투두는 false (category 'ai' 이지만 할일처럼 다룸).
   */
  aiGenerated?: boolean
  /**
   * AI 투두: 완료한 뒤 상태 입력까지 했는지.
   * 상태 입력 팝업은 AI 목표 흐름 쪽 작업이라, 지금은 아이콘 표시에만 씁니다.
   */
  aiStateEntered?: boolean
}

/**
 * 목업 투두 (Figma 투두리스트_기본상태의 목록 그대로, 10/26 갱신본)
 * Figma 에서는 '오늘'(10월 2일)에 들어 있어, 화면을 연 날(오늘)에 넣습니다.
 */
export function createMockTodos(today: string): LocalTodo[] {
  return [
    { id: 1, date: today, title: '한국 디자인사 (2) 전시 리포트 작성하기', category: 'task', done: false },
    { id: 2, date: today, title: '기업디 팀플 회의 10시', category: 'schedule', done: false },
    { id: 3, date: today, title: '한국 디자인사 (2) 전시 리포트 작성하기', category: 'task', done: true },
    { id: 4, date: today, title: '매일 영어 단어 30개 외우기', category: 'ai', done: true, aiGenerated: true, aiStateEntered: false },
    { id: 5, date: today, title: '한국 디자인사 (2) 전시 리포트 작성하기', category: 'ai', done: false, aiGenerated: true },
    { id: 6, date: today, title: '한국 디자인사 (2) 전시 리포트 작성하기', category: 'ai', done: false },
  ]
}
