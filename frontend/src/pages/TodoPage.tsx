import { useEffect, useMemo, useState } from 'react'
import { getApiError } from '../api/client'
import Calendar, { type CalendarMark } from '../components/Calendar'
import Toast from '../components/Toast'
import { getMonthCalendar, toCalendarMarks } from '../features/calendar/api'
import CategoryBar, { CategoryPointer } from '../features/todo/CategoryBar'
import MoveDateHeader from '../features/todo/MoveDateHeader'
import TodoCard, { TodoEmpty } from '../features/todo/TodoCard'
import TodoEditRow from '../features/todo/TodoEditRow'
import TodoInput from '../features/todo/TodoInput'
import TodoItem from '../features/todo/TodoItem'
import TodoMoreSheet from '../features/todo/TodoMoreSheet'
import { completeTodo, createTask, deleteTodo, getDailyTodos, moveTodo, updateTodoContent } from '../features/todo/api'
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

/** 캘린더에는 목록과 같은 종류만 표시합니다 (일반 할일 · AI 목표 투두). 일정은 아직 연동하지 않습니다. */
const MARK_TYPES = new Set(['TASK', 'AI_GOAL'])

/**
 * 연동 전까지 추가할 수 없는 카테고리.
 * TODO: 일정 · 기타는 백엔드 투두에 없고, AI 는 목표를 고르는 화면이 없어 지금은 할일만 추가할 수 있습니다.
 */
const ADDABLE: TodoCategoryKey[] = ['task']

/**
 * 투두리스트 화면 (Figma UI > 투두리스트 - 기본)
 *
 * 구조 (Figma Frame 2085668192: 세로 가운데 / 패딩 위 20 · 좌우 20 / 간격 20)
 * - 월간 캘린더 카드 (공용 Calendar)
 * - 날짜 제목 "2026년 10월 2일 금" 20px 700 행간 24px Black/Black
 * - Frame 2085668215 (간격 12): [카테고리 바 + (고르면) 꼭지 + 입력창, 간격 8] [투두 카드]
 *   투두가 없는 날은 "아직 투두리스트가 없어요." 카드 (투두리스트_투두리스트없을때)
 *
 * … 버튼 → 더보기 시트 (투두리스트 더보기)
 *   - 수정하기 → 그 줄이 수정 줄로 바뀜 (투두리스트_수정)
 *   - 삭제하기 → 지우고 "삭제 되었습니다." 토스트 (투두리스트_삭제 알림)
 *   - 날짜 변경 → 날짜 이동 화면 (투두리스트_날짜 이동)
 *     위에 안내 말풍선 + [취소하기 | 이동하기], 카테고리 바 대신 "이동중인 투두리스트" 와 그 투두,
 *     그 날의 나머지 투두는 흐리게(30%). 캘린더에서 날짜를 고르면 노란 원으로 표시되고,
 *     지난 날짜는 고를 수 없음 (디자이너 답변). 이동하기 → 그 날짜로 옮기고 그 날짜를 보여 주며 "이동되었습니다." 토스트
 *
 * 백엔드 연동 (features/todo/api)
 * - 목록: GET /api/v1/todos?date= (미완료 먼저, 완료는 아래)
 * - 캘린더 뱃지: GET /api/v1/calendar 로 보이는 달의 할일 · AI 목표 투두
 * - 할일 추가 · 내용 수정 · 완료 · 날짜 변경 · 삭제 후에는 그 날 목록과 캘린더를 다시 불러옵니다.
 * - 실패하면 토스트에 백엔드 문구 (실패 안내 디자인이 없어 회원가입 화면과 같은 방식)
 * 아직 안 되는 것 (백엔드 · 디자인 확인 필요): 일정 · 기타 · AI 추가, 완료 취소, 카테고리 변경
 */
export default function TodoPage() {
  const [today] = useState(startOfToday)
  const [selectedDate, setSelectedDate] = useState(today)
  /** 캘린더에 보이는 달 */
  const [month, setMonth] = useState({ year: today.getFullYear(), month: today.getMonth() + 1 })
  const [category, setCategory] = useState<TodoCategoryKey | null>(null)
  const [todos, setTodos] = useState<TodoView[]>([])
  const [marks, setMarks] = useState<Record<string, CalendarMark[]>>({})
  /** 더보기 시트를 연 투두 */
  const [moreId, setMoreId] = useState<number | null>(null)
  /** 수정 중인 투두 */
  const [editId, setEditId] = useState<number | null>(null)
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
        if (!ignore) showToast(getApiError(error)?.message ?? FALLBACK_ERROR)
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

  /** 요청 하나를 보내고, 성공하면 목록 · 캘린더를 다시 불러옵니다. 실패하면 백엔드 문구를 토스트로 */
  async function run(request: () => Promise<unknown>, successMessage?: string) {
    if (busy) return false
    setBusy(true)
    try {
      await request()
      if (successMessage) showToast(successMessage)
      return true
    } catch (error) {
      showToast(getApiError(error)?.message ?? FALLBACK_ERROR)
      return false
    } finally {
      setBusy(false)
      setReloadKey((key) => key + 1)
    }
  }

  function handleSelectDate(date: Date) {
    if (moving) {
      setMoving({ ...moving, target: date })
      return
    }
    setSelectedDate(date)
    setEditId(null)
  }

  function handleSelectCategory(key: TodoCategoryKey | null) {
    if (key && !ADDABLE.includes(key)) return
    setCategory(key)
  }

  function handleAdd(content: string) {
    if (category !== 'task') return
    void run(() => createTask(content, selectedKey))
  }

  /**
   * 체크박스: 완료.
   * TODO: 완료 취소 API 가 없어 완료한 투두는 체크박스를 누를 수 없게 둡니다.
   */
  function handleComplete(id: number) {
    void run(() => completeTodo(id))
  }

  function handleEdit() {
    setEditId(moreId)
    setMoreId(null)
  }

  /** TODO: 카테고리를 바꾸는 API 가 없어 내용만 저장합니다. */
  async function handleSave(todo: TodoView, title: string) {
    if (title === todo.title) {
      setEditId(null)
      return
    }
    if (await run(() => updateTodoContent(todo.id, title))) setEditId(null)
  }

  function handleDelete() {
    if (moreId === null) return
    const id = moreId
    setMoreId(null)
    void run(() => deleteTodo(id), '삭제 되었습니다.')
  }

  function handleStartMove() {
    const todo = todos.find((item) => item.id === moreId)
    if (!todo) return
    setMoving({ todo, target: null })
    setMoreId(null)
    setEditId(null)
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

  /**
   * 날짜 이동에서 고를 수 없는 날짜: 지난 날짜(디자이너 답변) + 원래 날짜와 그 이전(백엔드: 원래 날짜보다 뒤만 가능)
   * 날짜 키(YYYY-MM-DD)는 문자열 비교로 순서를 알 수 있습니다.
   */
  const isMoveDisabled = useMemo(() => {
    if (!moving) return undefined
    const from = moving.todo.date
    return (date: Date) => date < today || toDateKey(date) <= from
  }, [moving, today])

  return (
    <main className="relative flex w-full flex-col items-center gap-[20px] px-[20px] pt-[20px]">
      <Toast message={toast.message} top={TOAST_TOP} />

      {moving && (
        <MoveDateHeader target={moving.target} onCancel={() => setMoving(null)} onMove={() => void handleMove()} />
      )}

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

      {moving ? (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <h2 className="text-[18px] leading-[21px] font-semibold text-black-gray40">이동중인 투두리스트</h2>
            <TodoCard>
              <TodoItem todo={moving.todo} />
            </TodoCard>
          </div>
          {todos.some((todo) => todo.id !== moving.todo.id) && (
            <TodoCard faded>
              {todos
                .filter((todo) => todo.id !== moving.todo.id)
                .map((todo) => (
                  <TodoItem key={todo.id} todo={todo} />
                ))}
            </TodoCard>
          )}
        </div>
      ) : (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <CategoryBar selected={category} onSelect={handleSelectCategory} />
            {category && (
              <>
                <CategoryPointer selected={category} />
                <TodoInput category={getCategory(category)} onSubmit={handleAdd} />
              </>
            )}
          </div>

          {todos.length === 0 ? (
            <TodoEmpty />
          ) : (
            <TodoCard>
              {todos.map((todo) =>
                todo.id === editId ? (
                  <TodoEditRow
                    key={todo.id}
                    title={todo.title}
                    category={todo.category}
                    onSave={(title) => void handleSave(todo, title)}
                  />
                ) : (
                  <TodoItem
                    key={todo.id}
                    todo={todo}
                    onToggle={todo.done ? undefined : () => handleComplete(todo.id)}
                    onMore={() => setMoreId(todo.id)}
                  />
                ),
              )}
            </TodoCard>
          )}
        </div>
      )}

      <TodoMoreSheet
        open={moreId !== null}
        onEdit={handleEdit}
        onDelete={handleDelete}
        onMoveDate={handleStartMove}
        onClose={() => setMoreId(null)}
      />
    </main>
  )
}
