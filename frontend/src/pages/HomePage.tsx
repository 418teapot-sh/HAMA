import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import icRightBlack from '../assets/icons/ic_right_black.svg'
import Calendar, { type CalendarMark } from '../components/Calendar'
import { getMonthCalendar, toCalendarMarks } from '../features/calendar/api'
import { getCurrentGoal, getGoalTree, type GoalSummary, type GoalTree } from '../features/goal/api'
import GoalSummaryCard from '../features/goal/components/GoalSummaryCard'
import { paths } from '../paths'
import { formatKoreanDate, toDateKey } from '../utils/date'

/** 오늘이 기간 안에 있는 기간 목표(월/주) 제목들 */
function getCurrentPeriodGoals(tree: GoalTree | null) {
  if (!tree) return []
  const today = toDateKey(new Date())
  return tree.milestones
    .flatMap((milestone) => milestone.periodGoals)
    .filter((periodGoal) => periodGoal.startDate <= today && today <= periodGoal.endDate)
    .map((periodGoal) => periodGoal.title)
}

/**
 * 홈 (Figma: 홈)
 * - 화면 패딩 20·20·0·20, 섹션 간격 20
 * - 섹션: 제목(Heading/H4 Black/Black) ↔ 내용 간격 12
 */
export default function HomePage() {
  const [goal, setGoal] = useState<GoalSummary | null>(null)
  const [tree, setTree] = useState<GoalTree | null>(null)
  const [goalLoading, setGoalLoading] = useState(true)

  const [selectedDate, setSelectedDate] = useState(() => new Date())
  const [viewMonth, setViewMonth] = useState(() => ({
    year: selectedDate.getFullYear(),
    month: selectedDate.getMonth() + 1,
  }))
  const [marks, setMarks] = useState<Record<string, CalendarMark[]>>({})

  // 목표 요약: 처음 한 번
  useEffect(() => {
    let ignore = false

    async function load() {
      try {
        const current = await getCurrentGoal()
        const currentTree = current ? await getGoalTree(current.goalId) : null
        if (!ignore) {
          setGoal(current)
          setTree(currentTree)
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
  }, [viewMonth.year, viewMonth.month])

  return (
    <main className="flex flex-col gap-[20px] px-[20px] pt-[20px]">
      <section className="flex flex-col gap-[12px]">
        <h2 className="text-heading-h4 text-black-black">목표 요약</h2>
        {goalLoading ? null : goal ? (
          <GoalSummaryCard
            title={goal.title}
            startDate={goal.startDate}
            endDate={goal.endDate}
            items={getCurrentPeriodGoals(tree)}
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

      <section className="flex flex-col gap-[12px]">
        <Link to={paths.todos} className="flex items-center justify-between">
          <h2 className="text-heading-h4 text-black-black">{formatKoreanDate(selectedDate)}</h2>
          <img src={icRightBlack} alt="" className="size-[24px] shrink-0" />
        </Link>
        {/* TODO: 선택한 날짜의 투두 목록 (TodoList, 조민서 작업 후 연결) */}
      </section>
    </main>
  )
}
