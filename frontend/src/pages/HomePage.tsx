import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import icRightBlack from '../assets/icons/ic_right_black.svg'
import Calendar, { type CalendarMark } from '../components/Calendar'
import { getMonthCalendar, toCalendarMarks } from '../features/calendar/api'
import { getCurrentGoal, getGoalTasks, type GoalSummary, type GoalTask } from '../features/goal/api'
import GoalSummaryCard from '../features/goal/components/GoalSummaryCard'
import { paths } from '../paths'
import { formatKoreanDate } from '../utils/date'

/**
 * 홈 (Figma: 홈)
 * - 화면 패딩 20·20·0·20, 섹션 간격 20
 * - 섹션: 제목(Heading/H4 Black/Black) ↔ 내용 간격 12
 */
export default function HomePage() {
  const [goal, setGoal] = useState<GoalSummary | null>(null)
  const [tasks, setTasks] = useState<GoalTask[]>([])
  const [goalLoading, setGoalLoading] = useState(true)
  const [goalFailed, setGoalFailed] = useState(false)

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
        // 체크 목록 = 선택한 플랜의 반복 할 일 (GET /goals/{id}/tasks)
        const goalTasks = current ? await getGoalTasks(current.goalId).catch(() => null) : null
        if (!ignore) {
          setGoal(current)
          setTasks(goalTasks?.tasks ?? [])
        }
      } catch {
        if (!ignore) setGoalFailed(true)
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
        {goalLoading ? null : goalFailed ? (
          <p className="text-body-b8 text-black-gray60">목표를 불러오지 못했어요. 잠시 후 다시 시도해주세요.</p>
        ) : goal ? (
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
