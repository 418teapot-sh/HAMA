import { useMemo, useState } from 'react'
import Calendar, { type CalendarMark } from '../components/Calendar'
import Toast from '../components/Toast'
import CategoryBar, { CategoryPointer } from '../features/todo/CategoryBar'
import MoveDateHeader from '../features/todo/MoveDateHeader'
import TodoCard, { TodoEmpty } from '../features/todo/TodoCard'
import TodoEditRow from '../features/todo/TodoEditRow'
import TodoInput from '../features/todo/TodoInput'
import TodoItem from '../features/todo/TodoItem'
import TodoMoreSheet from '../features/todo/TodoMoreSheet'
import { getCategory, type TodoCategoryKey } from '../features/todo/categories'
import { createMockTodos, type LocalTodo } from '../features/todo/mock'
import { useToast } from '../hooks/useToast'
import { formatKoreanDate, startOfToday, toDateKey } from '../utils/date'

/**
 * 토스트 위치: 화면 내용 맨 위에서 415px (캘린더 카드 아래쪽 위에 겹침)
 * Figma 투두리스트_삭제 알림 · 투두리스트_날짜 이동(이동 뒤) 모두 Frame 2085668192 안에서 top 415, 가로 가운데
 */
const TOAST_TOP = 415

/** 완료한 투두를 아래로 (그 밖의 순서는 그대로, 디자이너 답변) */
function sortTodos(todos: LocalTodo[]) {
  return [...todos].sort((a, b) => Number(a.done) - Number(b.done))
}

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
 * 페이지 구현 단계라 백엔드 연동 없이 화면 안의 상태로만 동작합니다. (새로고침하면 목업으로 돌아감)
 * 처음에는 Figma 기본상태의 목업 투두를 오늘 날짜에 보여 줍니다.
 */
export default function TodoPage() {
  const [today] = useState(startOfToday)
  const [selectedDate, setSelectedDate] = useState(today)
  const [category, setCategory] = useState<TodoCategoryKey | null>(null)
  const [todos, setTodos] = useState<LocalTodo[]>(() => createMockTodos(toDateKey(today)))
  /** 더보기 시트를 연 투두 */
  const [moreId, setMoreId] = useState<number | null>(null)
  /** 수정 중인 투두 */
  const [editId, setEditId] = useState<number | null>(null)
  /** 날짜 이동 중인 투두와 고른 날짜 */
  const [moving, setMoving] = useState<{ id: number; target: Date | null } | null>(null)
  const toast = useToast()

  /** 캘린더 날짜 칸 표시: 그 날 투두 (목록과 같은 순서, 캘린더가 최대 3개 · 말줄임) */
  const marks = useMemo(() => {
    const byDate: Record<string, CalendarMark[]> = {}
    for (const todo of sortTodos(todos)) {
      ;(byDate[todo.date] ??= []).push({ id: todo.id, title: todo.title, category: todo.category })
    }
    return byDate
  }, [todos])

  const selectedKey = toDateKey(selectedDate)
  const items = sortTodos(todos.filter((todo) => todo.date === selectedKey))
  const movingTodo = moving ? todos.find((todo) => todo.id === moving.id) : undefined

  function handleSelectDate(date: Date) {
    if (moving) {
      setMoving({ ...moving, target: date })
      return
    }
    setSelectedDate(date)
    setEditId(null)
  }

  function handleAdd(content: string) {
    if (!category) return
    setTodos((prev) => [
      ...prev,
      { id: Math.max(0, ...prev.map((todo) => todo.id)) + 1, date: selectedKey, title: content, category, done: false },
    ])
  }

  /** 체크박스: 완료 ↔ 완료 취소 */
  function handleToggle(id: number) {
    setTodos((prev) => prev.map((todo) => (todo.id === id ? { ...todo, done: !todo.done } : todo)))
  }

  function handleEdit() {
    setEditId(moreId)
    setMoreId(null)
  }

  function handleSave(id: number, title: string, nextCategory: TodoCategoryKey) {
    setTodos((prev) => prev.map((todo) => (todo.id === id ? { ...todo, title, category: nextCategory } : todo)))
    setEditId(null)
  }

  function handleDelete() {
    setTodos((prev) => prev.filter((todo) => todo.id !== moreId))
    setMoreId(null)
    toast.show('삭제 되었습니다.')
  }

  function handleStartMove() {
    if (moreId === null) return
    setMoving({ id: moreId, target: null })
    setMoreId(null)
    setEditId(null)
    setCategory(null)
  }

  function handleMove() {
    if (!moving?.target) return
    const target = moving.target
    setTodos((prev) => prev.map((todo) => (todo.id === moving.id ? { ...todo, date: toDateKey(target) } : todo)))
    setSelectedDate(target)
    setMoving(null)
    toast.show('이동되었습니다.')
  }

  return (
    <main className="relative flex w-full flex-col items-center gap-[20px] px-[20px] pt-[20px]">
      <Toast message={toast.message} top={TOAST_TOP} />

      {moving && (
        <MoveDateHeader target={moving.target} onCancel={() => setMoving(null)} onMove={handleMove} />
      )}

      <Calendar
        selectedDate={selectedDate}
        onSelectDate={handleSelectDate}
        marks={marks}
        highlightDate={moving?.target}
        isDateDisabled={moving ? (date) => date < today : undefined}
      />

      <div className="flex h-[24px] w-full items-center justify-between gap-[10px]">
        <h1 className="text-heading-h4 leading-[24px] text-black-black">{formatKoreanDate(selectedDate)}</h1>
      </div>

      {moving && movingTodo ? (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <h2 className="text-[18px] leading-[21px] font-semibold text-black-gray40">이동중인 투두리스트</h2>
            <TodoCard>
              <TodoItem todo={movingTodo} />
            </TodoCard>
          </div>
          {items.length > 1 && (
            <TodoCard faded>
              {items
                .filter((todo) => todo.id !== movingTodo.id)
                .map((todo) => (
                  <TodoItem key={todo.id} todo={todo} />
                ))}
            </TodoCard>
          )}
        </div>
      ) : (
        <div className="flex w-full flex-col gap-[12px]">
          <div className="flex w-full flex-col gap-[8px]">
            <CategoryBar selected={category} onSelect={setCategory} />
            {category && (
              <>
                <CategoryPointer selected={category} />
                <TodoInput category={getCategory(category)} onSubmit={handleAdd} />
              </>
            )}
          </div>

          {items.length === 0 ? (
            <TodoEmpty />
          ) : (
            <TodoCard>
              {items.map((todo) =>
                todo.id === editId ? (
                  <TodoEditRow
                    key={todo.id}
                    title={todo.title}
                    category={todo.category}
                    onSave={(title, nextCategory) => handleSave(todo.id, title, nextCategory)}
                  />
                ) : (
                  <TodoItem
                    key={todo.id}
                    todo={todo}
                    onToggle={() => handleToggle(todo.id)}
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
