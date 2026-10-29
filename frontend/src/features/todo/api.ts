import { api } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import type { TodoCategoryKey } from './categories'
import type { TodoView } from './types'

/**
 * 투두 API (/api/v1/todos). 요청·응답 형식은 Swagger(https://api.todohama.site/swagger-ui/index.html) 기준입니다.
 * 백엔드 투두 종류는 TASK(일반 할일) · AI_GOAL_TASK(목표 투두) 두 가지뿐입니다.
 * TODO: 화면의 일정 · 기타 카테고리는 백엔드 투두에 없어 아직 연동하지 않습니다.
 */
export type TodoApiCategory = 'TASK' | 'AI_GOAL_TASK'

/** 미완료 투두 */
interface PendingTodo {
  todoId: number
  category: TodoApiCategory
  content: string
  goalId: number | null
  goalTitle: string | null
  /** 'HH:mm' */
  startTime: string | null
  endTime: string | null
  postponedCount: number
}

/** 완료 투두 */
interface CompletedTodo {
  todoId: number
  category: TodoApiCategory
  content: string
  /** 'yyyy-MM-ddTHH:mm:ss' */
  completedAt: string
  statusNote: string | null
}

interface DailyTodos {
  /** 'YYYY-MM-DD' */
  date: string
  pending: PendingTodo[]
  completed: CompletedTodo[]
}

const CATEGORY: Record<TodoApiCategory, TodoCategoryKey> = {
  TASK: 'task',
  AI_GOAL_TASK: 'ai',
}

/** 응답 → 화면 투두. 미완료 먼저, 완료는 아래 (디자이너 답변: 완료한 투두를 아래로) */
function toTodoViews({ date, pending, completed }: DailyTodos): TodoView[] {
  return [
    ...pending.map((todo) => ({
      id: todo.todoId,
      date,
      title: todo.content,
      category: CATEGORY[todo.category],
      done: false,
      aiGenerated: todo.category === 'AI_GOAL_TASK',
    })),
    ...completed.map((todo) => ({
      id: todo.todoId,
      date,
      title: todo.content,
      category: CATEGORY[todo.category],
      done: true,
      aiGenerated: todo.category === 'AI_GOAL_TASK',
      aiStateEntered: !!todo.statusNote,
    })),
  ]
}

/** 날짜별 투두 (GET /api/v1/todos?date=YYYY-MM-DD) */
export async function getDailyTodos(date: string) {
  const res = await api.get<ApiResponse<DailyTodos>>('/api/v1/todos', { params: { date } })
  return toTodoViews(res.data.data)
}

/**
 * 일반 할일 추가 (POST /api/v1/todos). 시간은 화면에서 받지 않아 비웁니다.
 * TODO: AI 목표에 투두를 직접 추가하려면 goalId 가 필요한데, 목표를 고르는 화면이 아직 없습니다.
 */
export async function createTask(content: string, todoDate: string) {
  const res = await api.post<ApiResponse<{ todoId: number }>>('/api/v1/todos', {
    category: 'TASK',
    content,
    todoDate,
  })
  return res.data.data.todoId
}

/** 내용 수정 (PATCH /api/v1/todos/{id}). 완료한 투두는 409 TODO_ALREADY_COMPLETED */
export async function updateTodoContent(todoId: number, content: string) {
  await api.patch(`/api/v1/todos/${todoId}`, { content })
}

/**
 * 완료 (PATCH /api/v1/todos/{id}/complete). 이미 완료면 409.
 * TODO: 완료 취소 API 가 없어, 완료한 투두는 체크를 풀 수 없습니다.
 */
export async function completeTodo(todoId: number) {
  await api.patch(`/api/v1/todos/${todoId}/complete`)
}

/**
 * 날짜 변경 (PATCH /api/v1/todos/{id}/postpone). 일반 할일은 원래 날짜보다 뒤의 날짜만 됩니다.
 * 완료한 투두는 409.
 */
export async function moveTodo(todoId: number, targetDate: string) {
  await api.patch(`/api/v1/todos/${todoId}/postpone`, { targetDate })
}

/** 삭제 (DELETE /api/v1/todos/{id}) */
export async function deleteTodo(todoId: number) {
  await api.delete(`/api/v1/todos/${todoId}`)
}
