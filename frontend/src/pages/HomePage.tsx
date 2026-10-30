import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { getApiError } from '../api/client'
import icRightBlack from '../assets/icons/ic_right_black.svg'
import Calendar, { type CalendarMark } from '../components/Calendar'
import Toast from '../components/Toast'
import { getMonthCalendar, toCalendarMarks } from '../features/calendar/api'
import { getCurrentGoal, getGoalTasks, type GoalSummary, type GoalTask } from '../features/goal/api'
import GoalSummaryCard from '../features/goal/components/GoalSummaryCard'
import { AiStatePromptModal } from '../features/todo/AiStateSheets'
import TodoCard, { TodoEmpty } from '../features/todo/TodoCard'
import TodoItem from '../features/todo/TodoItem'
import { completeTodo, getDailyTodos, setScheduleDone, uncompleteTodo } from '../features/todo/api'
import type { TodoView } from '../features/todo/types'
import { useToast } from '../hooks/useToast'
import type { TodoPageState } from './TodoPage'
import { paths } from '../paths'
import { formatKoreanDate, toDateKey } from '../utils/date'

/** 서버가 문구를 주지 않았을 때(네트워크 오류 등) 보여줄 문구 (투두 화면과 같음) */
const FALLBACK_ERROR = '잠시 후 다시 시도해 주세요'

/**
 * 홈 (Figma: 홈)
 * - 화면 패딩 20·20·0·20, 섹션 간격 20
 * - 섹션: 제목(Heading/H4 Black/Black) ↔ 내용 간격 12
 * - 날짜 섹션: 고른 날짜의 투두 · 일정 목록 (투두 화면과 같은 TodoCard · TodoItem)
 *   체크박스로 완료 ↔ 완료 취소 (일정도 날짜별 완료, #236). AI 목표 투두를 완료하면 "현재 상태를 입력할까요?" 팝업
 *   … · sparkle · 상태 입력하기 → 투두 화면(그 날짜)으로 이동해 수정 · 상태 입력
 */
export default function HomePage() {
  const [goal, setGoal] = useState<GoalSummary | null>(null)
  const [tasks, setTasks] = useState<GoalTask[]>([])
  const [goalLoading, setGoalLoading] = useState(true)

  const [selectedDate, setSelectedDate] = useState(() => new Date())
  const [viewMonth, setViewMonth] = useState(() => ({
    year: selectedDate.getFullYear(),
    month: selectedDate.getMonth() + 1,
  }))
  const [marks, setMarks] = useState<Record<string, CalendarMark[]>>({})

  const navigate = useNavigate()
  const toast = useToast()
  const showToast = toast.show
  const [todos, setTodos] = useState<TodoView[]>([])
  const [todosLoaded, setTodosLoaded] = useState(false)
  const [busy, setBusy] = useState(false)
  /** 늘리면 목록 · 캘린더 뱃지를 다시 불러옵니다 (체크 뒤) */
  const [reloadKey, setReloadKey] = useState(0)
  /** 완료 뒤 "현재 상태를 입력할까요?" 팝업을 띄울 AI 목표 투두 */
  const [promptTodo, setPromptTodo] = useState<TodoView | null>(null)
  const selectedKey = toDateKey(selectedDate)

  // 목표 요약: 처음 한 번
  useEffect(() => {
    let ignore = false

    async function load() {
      try {
        const current = await getCurrentGoal()
        // 체크 목록 = 선택한 플랜의 반복 할 일 (GET /goals/{id}/tasks)
        const goalTasks = current ? await getGoalTasks(current.goalId) : null
        if (!ignore) {
          setGoal(current)
          setTasks(goalTasks?.tasks ?? [])
        }
      } finally {
        if (!ignore) setGoalLoading(false)
      }
    }

    load()
    return () => {
      ignore = true
    }
  }, [])

  // 캘린더: 보고 있는 달이 바뀔 때마다
  useEffect(() => {
    let ignore = false

    getMonthCalendar(viewMonth.year, viewMonth.month)
      .then((items) => {
        if (!ignore) setMarks(toCalendarMarks(items))
      })
      .catch(() => {
        if (!ignore) setMarks({})
      })

    return () => {
      ignore = true
    }
  }, [viewMonth.year, viewMonth.month, reloadKey])

  // 고른 날짜의 투두 · 일정
  useEffect(() => {
    let ignore = false
    getDailyTodos(selectedKey)
      .then((items) => {
        if (!ignore) setTodos(items)
      })
      .catch(() => {
        if (!ignore) setTodos([])
      })
      .finally(() => {
        if (!ignore) setTodosLoaded(true)
      })
    return () => {
      ignore = true
    }
  }, [selectedKey, reloadKey])

  /** 투두 화면으로 이동 (그 날짜를 보여 주고, stateTodoId 가 있으면 현재 상태 입력을 바로 엶) */
  function goTodos(state: TodoPageState = {}) {
    navigate(paths.todos, { state: { date: selectedKey, ...state } satisfies TodoPageState })
  }

  /** 체크박스: 완료 ↔ 완료 취소 */
  async function handleToggle(todo: TodoView) {
    if (busy) return
    setBusy(true)
    try {
      if (todo.kind === 'schedule') await setScheduleDone(todo, !todo.done)
      else if (todo.done) await uncompleteTodo(todo.id)
      else {
        await completeTodo(todo.id)
        if (todo.category === 'ai') setPromptTodo(todo)
      }
    } catch (error) {
      showToast(getApiError(error)?.message ?? FALLBACK_ERROR)
    } finally {
      setBusy(false)
      setReloadKey((key) => key + 1)
    }
  }

  return (
    <main className="flex flex-col gap-[20px] px-[20px] pt-[20px]">
      <section className="flex flex-col gap-[12px]">
        <h2 className="text-heading-h4 text-black-black">목표 요약</h2>
        {goalLoading ? null : goal ? (
          <GoalSummaryCard
            title={goal.title}
            startDate={goal.startDate}
            endDate={goal.endDate}
            items={tasks.map((task) => task.content)}
          />
        ) : (
          <p className="text-body-b8 text-black-gray60">진행 중인 목표가 없어요.</p>
        )}
      </section>

      <section className="flex flex-col gap-[12px]">
        <h2 className="text-heading-h4 text-black-black">캘린더</h2>
        <Calendar
          selectedDate={selectedDate}
          onSelectDate={setSelectedDate}
          marks={marks}
          onMonthChange={(year, month) => setViewMonth({ year, month })}
        />
      </section>

      <section className="flex flex-col gap-[12px] pb-[20px]">
        <Link
          to={paths.todos}
          state={{ date: selectedKey } satisfies TodoPageState}
          className="flex items-center justify-between"
        >
          <h2 className="text-heading-h4 text-black-black">{formatKoreanDate(selectedDate)}</h2>
          <img src={icRightBlack} alt="" className="size-[24px] shrink-0" />
        </Link>
        {todos.length === 0 ? (
          todosLoaded && <TodoEmpty />
        ) : (
          <TodoCard>
            {todos.map((todo) => (
              <TodoItem
                key={todo.key}
                todo={todo}
                onToggle={() => void handleToggle(todo)}
                onMore={todo.scheduleType === 'FIXED' ? undefined : () => goTodos()}
                onSparkle={() => goTodos({ stateTodoId: todo.id })}
              />
            ))}
          </TodoCard>
        )}
      </section>

      <Toast message={toast.message} />

      <AiStatePromptModal
        open={promptTodo !== null}
        onClose={() => setPromptTodo(null)}
        onConfirm={() => {
          const id = promptTodo?.id
          setPromptTodo(null)
          goTodos({ stateTodoId: id })
        }}
      />
    </main>
  )
}
