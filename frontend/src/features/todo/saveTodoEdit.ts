import type { TodoEditValue } from './components/TodoEditRow'
import type { TodoCategoryKey } from './categories'
import { convertCategory, toDateTime, updateScheduleTitle, updateTodo } from './api'
import type { TodoView } from './types'

/** 화면 분류 → 백엔드 투두 종류 (할일 · 기타) */
export function toTodoCategory(key: TodoCategoryKey): 'TASK' | 'OTHER' {
  return key === 'etc' ? 'OTHER' : 'TASK'
}

/**
 * 투두 수정 저장 요청을 만듭니다. 바뀐 게 없으면 null.
 * - 일정 → 일정: 제목만 PATCH
 * - 일정 ↔ 할일 · 기타: 분류 전환 API 로 바꾼 뒤, 제목이 바뀌었으면 새 항목의 제목을 고침
 * - 할일 ↔ 기타: PATCH 한 번 (AI 목표 투두는 분류를 바꾸지 않음)
 */
export function buildSaveRequest(todo: TodoView, value: TodoEditValue): (() => Promise<unknown>) | null {
  const titleChanged = value.title !== todo.title
  const categoryChanged = value.category !== todo.category
  if (!titleChanged && !categoryChanged) return null

  if (todo.kind === 'schedule') {
    if (value.category === 'schedule') return () => updateScheduleTitle(todo.id, value.title)
    // 일정 → 할일 · 기타: 같은 날짜, 시간 없음
    return async () => {
      const converted = await convertCategory({
        sourceType: 'PERSONAL',
        refId: todo.id,
        targetType: toTodoCategory(value.category),
        todoDate: todo.date,
        startTime: null,
        endTime: null,
      })
      if (titleChanged) await updateTodo(converted.refId, { content: value.title })
    }
  }

  if (value.category === 'schedule') {
    // 할일 · 기타 → 일정: 고른 시간으로
    return async () => {
      const converted = await convertCategory({
        sourceType: todo.apiCategory === 'OTHER' ? 'OTHER' : 'TASK',
        refId: todo.id,
        targetType: 'PERSONAL',
        startAt: toDateTime(todo.date, value.startTime!),
        endAt: toDateTime(todo.date, value.endTime!),
      })
      if (titleChanged) await updateScheduleTitle(converted.refId, value.title)
    }
  }

  return () =>
    updateTodo(todo.id, {
      ...(titleChanged ? { content: value.title } : {}),
      ...(categoryChanged && todo.category !== 'ai' ? { category: toTodoCategory(value.category) } : {}),
    })
}
