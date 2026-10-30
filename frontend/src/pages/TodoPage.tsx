import { useEffect, useMemo, useState } from 'react'
import { getApiError } from '../api/client'
import Calendar, { type CalendarMark } from '../components/Calendar'
import Toast from '../components/Toast'
import { getMonthCalendar, toCalendarMarks } from '../features/calendar/api'
import { getGoals, type GoalSummary } from '../features/goal/api'
import { AiStateInput, AiStatePromptModal, AiStateSheet, GoalPickSheet } from '../features/todo/AiStateSheets'
import CategoryBar, { CategoryPointer } from '../features/todo/CategoryBar'
import MoveDateHeader from '../features/todo/MoveDateHeader'
import TodoCard, { TodoEmpty } from '../features/todo/TodoCard'
import TodoEditRow, { type TodoEditValue } from '../features/todo/TodoEditRow'
import TodoInput, { type TodoInputValue } from '../features/todo/TodoInput'
import TodoItem from '../features/todo/TodoItem'
import TodoMoreSheet from '../features/todo/TodoMoreSheet'
import {
  completeTodo,
  convertCategory,
  createGoalTodo,
  createSchedule,
  createTodo,
  deleteSchedule,
  deleteTodo,
  getDailyTodos,
  moveTodo,
  toDateTime,
  uncompleteTodo,
  updateScheduleTitle,
  updateStatusNote,
  updateTodo,
} from '../features/todo/api'
import { getCategory, type TodoCategoryKey } from '../features/todo/categories'
import type { TodoView } from '../features/todo/types'
import { useToast } from '../hooks/useToast'
import { formatKoreanDate, startOfToday, toDateKey } from '../utils/date'

/**
 * 토스트 위치: 화면 내용 맨 위에서 415px (캘린더 카드 아래쪽 위에 겹침)
 * Figma 투두리스트_삭제 알림 · 투두리스트_날짜 이동(이동 뒤) 모두 Frame 2085668192 안에서 top 415, 가로 가운데
 */
const TOAST_TOP = 415

/** 서버가 문구를 주지 않았을 때(네트워크 오류 등) 보여줄 문구. 회원가입 화면과 같습니다. */
const FALLBACK_ERROR = '잠시 후 다시 시도해 주세요'

/** 에러 코드별 안내 (서버 문구보다 화면에 맞는 문구가 필요한 것만) */
const ERROR_MESSAGES: Record<string, string> = {
  TODO_AI_EDIT_NOT_ALLOWED: 'AI 목표는 수정 및 삭제가 불가능합니다.',
  TODO_ALREADY_COMPLETED: '완료한 투두는 체크를 풀고 수정해 주세요.',
  TODO_NOT_COMPLETED: '이미 완료하지 않은 투두예요.',
}

function errorMessage(error: unknown) {
  const apiError = getApiError(error)
  if (!apiError) return FALLBACK_ERROR
  return ERROR_MESSAGES[apiError.code] ?? apiError.message ?? FALLBACK_ERROR
}

/** 캘린더 뱃지: 할일 · 기타 · AI 목표 투두 · 일정 */
const MARK_TYPES = new Set(['TASK', 'OTHER', 'AI_GOAL', 'FIXED', 'PERSONAL'])

/** 화면 분류 → 백엔드 투두 종류 (할일 · 기타) */
function toTodoCategory(key: TodoCategoryKey): 'TASK' | 'OTHER' {
  return key === 'etc' ? 'OTHER' : 'TASK'
}

/**
 * 투두리스트 화면 (Figma UI > 투두리스트 - 기본 · AI 목표)
 *
 * 구조 (Figma Frame 2085668192: 세로 가운데 / 패딩 위 20 · 좌우 20 / 간격 20)
 * - 월간 캘린더 카드 (공용 Calendar)
 * - 날짜 제목 "2026년 10월 2일 금" 20px 700 행간 24px Black/Black
 * - Frame 2085668215 (간격 12): [카테고리 바 + (고르면) 꼭지 + 입력창, 간격 8] [투두 카드]
 *   투두가 없는 날은 "아직 투두리스트가 없어요." 카드 (투두리스트_투두리스트없을때)
 *
 * 추가: 할일 · 기타 (POST /todos), 일정 (POST /calendar/schedules, 시간 필요),
 *       AI (목표 고르기 시트 → POST /todos AI_GOAL_TASK + goalId)
 * 체크박스: 완료 ↔ 완료 취소. AI 목표 투두를 완료하면 "현재 상태를 입력할까요?" 팝업
 * … 버튼 → 더보기 시트 (투두리스트 더보기)
 *   - 수정하기 → 그 줄이 수정 줄로 (캐릭터로 할일 · 일정 · 기타 바꾸기, 투두리스트_수정)
 *   - 삭제하기 → 지우고 "삭제 되었습니다." 토스트
 *   - 날짜 변경 → 날짜 이동 화면 (투두리스트_날짜 이동)
 *   완료한 투두는 삭제만, 일정은 수정 · 삭제만 (날짜 변경 없음)
 * AI 가 만든 투두는 … 대신 sparkle → AI 목표 상태수정 시트 → 현재 상태 입력 (PATCH /todos/state/{id})
 */
export default function TodoPage() {
  const [today] = useState(startOfToday)
  const [selectedDate, setSelectedDate] = useState(today)
  /** 캘린더에 보이는 달 */
  const [month, setMonth] = useState({ year: today.getFullYear(), month: today.getMonth() + 1 })
  const [category, setCategory] = useState<TodoCategoryKey | null>(null)
  /** AI 칸에서 고른 목표 */
  const [goal, setGoal] = useState<GoalSummary | null>(null)
  const [goalPicking, setGoalPicking] = useState<GoalSummary[] | null>(null)
  const [todos, setTodos] = useState<TodoView[]>([])
  const [loaded, setLoaded] = useState(false)
  const [marks, setMarks] = useState<Record<string, CalendarMark[]>>({})
  /** 더보기 시트를 연 투두 (key) */
  const [moreKey, setMoreKey] = useState<string | null>(null)
  /** sparkle 시트를 연 AI 투두 (key) */
  const [sparkleKey, setSparkleKey] = useState<string | null>(null)
  /** 완료 뒤 "현재 상태를 입력할까요?" 팝업을 띄울 투두 */
  const [promptTodo, setPromptTodo] = useState<TodoView | null>(null)
  /** 현재 상태를 입력 중인 투두 */
  const [stateTodo, setStateTodo] = useState<TodoView | null>(null)
  /** 수정 중인 투두 (key) */
  const [editKey, setEditKey] = useState<string | null>(null)
  /** 날짜 이동 중인 투두와 고른 날짜 */
  const [moving, setMoving] = useState<{ todo: TodoView; target: Date | null } | null>(null)
  /** 요청 중에는 같은 동작을 또 보내지 않습니다 */
  const [busy, setBusy] = useState(false)
  const toast = useToast()
  const showToast = toast.show

  const selectedKey = toDateKey(selectedDate)

  /** 늘리면 목록 · 캘린더를 다시 불러옵니다 (추가 · 수정 · 완료 · 이동 · 삭제 뒤) */
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let ignore = false
    getDailyTodos(selectedKey)
      .then((items) => {
        if (!ignore) setTodos(items)
      })
      .catch((error: unknown) => {
        if (!ignore) showToast(errorMessage(error))
      })
      .finally(() => {
        if (!ignore) setLoaded(true)
      })
    return () => {
      ignore = true
    }
  }, [selectedKey, reloadKey, showToast])

  useEffect(() => {
    let ignore = false
    getMonthCalendar(month.year, month.month)
      .then((items) => {
        if (!ignore) setMarks(toCalendarMarks(items.filter((item) => MARK_TYPES.has(item.type))))
      })
      .catch(() => {
        // 캘린더 뱃지는 보조 정보라, 실패해도 목록은 그대로 씁니다.
      })
    return () => {
      ignore = true
    }
  }, [month.year, month.month, reloadKey])

  /** 요청 하나를 보내고, 끝나면 목록 · 캘린더를 다시 불러옵니다. 실패하면 토스트 */
  async function run(request: () => Promise<unknown>, successMessage?: string) {
    if (busy) return false
    setBusy(true)
    try {
      await request()
      if (successMessage) showToast(successMessage)
      return true
    } catch (error) {
      showToast(errorMessage(error))
      return false
    } finally {
      setBusy(false)
      setReloadKey((key) => key + 1)
    }
  }

  const findTodo = (key: string | null) => todos.find((item) => item.key === key)

  function handleSelectDate(date: Date) {
    if (moving) {
      setMoving({ ...moving, target: date })
      return
    }
    setSelectedDate(date)
    setEditKey(null)
    setStateTodo(null)
  }

  async function handleSelectCategory(key: TodoCategoryKey | null) {
    if (key !== 'ai') {
      setCategory(key)
      setGoal(null)
      return
    }
    // AI 칸: 진행 중인 목표를 골라야 추가할 수 있습니다 (Figma AI 목표 추가 시트)
    try {
      const goals = await getGoals('IN_PROGRESS')
      setGoalPicking(goals.filter((item) => item.status === 'IN_PROGRESS'))
    } catch (error) {
      showToast(errorMessage(error))
    }
  }

  function handlePickGoal(picked: GoalSummary) {
    setGoalPicking(null)
    if (selectedKey < picked.startDate || selectedKey > picked.endDate) {
      showToast('목표 기간 안의 날짜에만 추가할 수 있어요.')
      return
    }
    setGoal(picked)
    setCategory('ai')
  }

  function handleAdd({ content, startTime, endTime }: TodoInputValue) {
    if (!category) return Promise.resolve(false)
    if (category === 'ai') {
      if (!goal) return Promise.resolve(false)
      return run(() => createGoalTodo(goal.goalId, content, selectedKey))
    }
    if (category === 'schedule') {
      return run(() => createSchedule(content, selectedKey, startTime!, endTime!))
    }
    return run(() => createTodo(toTodoCategory(category), content, selectedKey))
  }

  /** 체크박스: 완료 ↔ 완료 취소. AI 목표 투두를 완료하면 현재 상태 입력 팝업 */
  async function handleToggle(todo: TodoView) {
    if (todo.done) {
      await run(() => uncompleteTodo(todo.id))
      return
    }
    const ok = await run(() => completeTodo(todo.id))
    if (ok && todo.category === 'ai') setPromptTodo(todo)
  }

  function handleEdit() {
    setEditKey(moreKey)
    setMoreKey(null)
  }

  /** 수정 저장: 내용 + 분류. 할일 ↔ 기타는 PATCH, 일정과 오가는 것은 전환 API */
  async function handleSave(todo: TodoView, value: TodoEditValue) {
    const titleChanged = value.title !== todo.title
    const categoryChanged = value.category !== todo.category
    if (!titleChanged && !categoryChanged) {
      setEditKey(null)
      return
    }

    let request: () => Promise<unknown>
    if (todo.kind === 'schedule') {
      if (value.category === 'schedule') {
        request = () => updateScheduleTitle(todo.id, value.title)
      } else {
        // 일정 → 할일 · 기타: 같은 날짜, 시간 없음
        request = async () => {
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
    } else if (value.category === 'schedule') {
      // 할일 · 기타 → 일정: 고른 시간으로
      request = async () => {
        const converted = await convertCategory({
          sourceType: todo.apiCategory === 'OTHER' ? 'OTHER' : 'TASK',
          refId: todo.id,
          targetType: 'PERSONAL',
          startAt: toDateTime(todo.date, value.startTime!),
          endAt: toDateTime(todo.date, value.endTime!),
        })
        if (titleChanged) await updateScheduleTitle(converted.refId, value.title)
      }
    } else {
      request = () =>
        updateTodo(todo.id, {
          ...(titleChanged ? { content: value.title } : {}),
          ...(categoryChanged && todo.category !== 'ai' ? { category: toTodoCategory(value.category) } : {}),
        })
    }
    if (await run(request)) setEditKey(null)
  }

  function handleDelete() {
    const todo = findTodo(moreKey)
    setMoreKey(null)
    if (!todo) return
    void run(() => (todo.kind === 'schedule' ? deleteSchedule(todo.id) : deleteTodo(todo.id)), '삭제 되었습니다.')
  }

  function handleStartMove() {
    const todo = findTodo(moreKey)
    if (!todo) return
    setMoving({ todo, target: null })
    setMoreKey(null)
    setEditKey(null)
    setCategory(null)
  }

  async function handleMove() {
    if (!moving?.target) return
    const { todo, target } = moving
    if (await run(() => moveTodo(todo.id, toDateKey(target)), '이동되었습니다.')) {
      setSelectedDate(target)
      setMoving(null)
    }
  }

  function startStateInput(todo: TodoView | undefined | null) {
    setPromptTodo(null)
    setSparkleKey(null)
    if (!todo) return
    setStateTodo(todo)
    setEditKey(null)
    setCategory(null)
  }

  async function handleSaveState(note: string) {
    if (!stateTodo) return
    const todo = stateTodo
    if (await run(() => updateStatusNote(todo.id, note), note ? '현재 상태를 저장했어요.' : undefined)) {
      setStateTodo(null)
    }
  }

  /**
   * 날짜 이동에서 고를 수 없는 날짜: 지난 날짜(디자이너 답변) + 원래 날짜와 그 이전(백엔드: 원래 날짜보다 뒤만 가능)
   * 날짜 키(YYYY-MM-DD)는 문자열 비교로 순서를 알 수 있습니다.
   */
  const isMoveDisabled = useMemo(() => {
    if (!moving) return undefined
    const from = moving.todo.date
    return (date: Date) => date < today || toDateKey(date) <= from
  }, [moving, today])

  const moreTodo = findTodo(moreKey)
  /** 목록 위에 따로 보여 주는 투두 (날짜 이동 · 상태 입력 중) */
  const focused = moving?.todo ?? stateTodo

  return (
    <main className="relative flex w-full flex-col items-center gap-[20px] px-[20px] pt-[20px]">
      <Toast message={toast.message} top={TOAST_TOP} />

      {moving && <MoveDateHeader target={moving.target} onCancel={() => setMoving(null)} onMove={() => void handleMove()} />}

      <Calendar
        selectedDate={selectedDate}
        onSelectDate={handleSelectDate}
        onMonthChange={(year, monthNumber) => setMonth({ year, month: monthNumber })}
        marks={marks}
        highlightDate={moving?.target}
        isDateDisabled={isMoveDisabled}
      />

      <div className="flex h-[24px] w-full items-center justify-between gap-[10px]">
        <h1 className="text-heading-h4 leading-[24px] text-black-black">{formatKoreanDate(selectedDate)}</h1>
      </div>

      {focused ? (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <div className="flex items-center justify-between">
              <h2 className="text-[18px] leading-[21px] font-semibold text-black-gray40">
                {moving ? '이동중인 투두리스트' : 'AI 목표 현재 상태 입력'}
              </h2>
              {stateTodo && (
                <button type="button" className="text-body-b8 text-black-gray60" onClick={() => setStateTodo(null)}>
                  취소
                </button>
              )}
            </div>
            <TodoCard>
              <TodoItem todo={{ ...focused, statusNote: null }} />
            </TodoCard>
            {stateTodo && <AiStateInput initial={stateTodo.statusNote ?? ''} busy={busy} onSubmit={(note) => void handleSaveState(note)} />}
          </div>
          {todos.some((todo) => todo.key !== focused.key) && (
            <TodoCard faded>
              {todos
                .filter((todo) => todo.key !== focused.key)
                .map((todo) => (
                  <TodoItem key={todo.key} todo={todo} />
                ))}
            </TodoCard>
          )}
        </div>
      ) : (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <CategoryBar selected={category} onSelect={(key) => void handleSelectCategory(key)} />
            {category && (
              <>
                <CategoryPointer selected={category} />
                <TodoInput
                  key={`${category}-${goal?.goalId ?? ''}`}
                  category={getCategory(category)}
                  withTime={category === 'schedule'}
                  placeholder={category === 'ai' && goal ? goal.title : undefined}
                  onSubmit={handleAdd}
                />
              </>
            )}
          </div>

          {todos.length === 0 ? (
            loaded && <TodoEmpty />
          ) : (
            <TodoCard>
              {todos.map((todo) =>
                todo.key === editKey ? (
                  <TodoEditRow
                    key={todo.key}
                    title={todo.title}
                    category={todo.category}
                    allowCategory={todo.category !== 'ai'}
                    onSave={(value) => void handleSave(todo, value)}
                  />
                ) : (
                  <TodoItem
                    key={todo.key}
                    todo={todo}
                    onToggle={todo.kind === 'todo' ? () => void handleToggle(todo) : undefined}
                    onMore={todo.scheduleType === 'FIXED' ? undefined : () => setMoreKey(todo.key)}
                    onSparkle={() => setSparkleKey(todo.key)}
                  />
                ),
              )}
            </TodoCard>
          )}
        </div>
      )}

      <TodoMoreSheet
        open={moreTodo !== undefined}
        canEdit={!moreTodo?.done}
        canMove={moreTodo?.kind === 'todo' && !moreTodo.done}
        onEdit={handleEdit}
        onDelete={handleDelete}
        onMoveDate={handleStartMove}
        onClose={() => setMoreKey(null)}
      />

      <AiStateSheet
        open={sparkleKey !== null}
        onClose={() => setSparkleKey(null)}
        onEditState={() => startStateInput(findTodo(sparkleKey))}
      />

      <AiStatePromptModal
        open={promptTodo !== null}
        onClose={() => setPromptTodo(null)}
        onConfirm={() => startStateInput(todos.find((item) => item.key === promptTodo?.key) ?? promptTodo)}
      />

      <GoalPickSheet
        open={goalPicking !== null}
        goals={goalPicking ?? []}
        onClose={() => setGoalPicking(null)}
        onPick={handlePickGoal}
      />
    </main>
  )
}
