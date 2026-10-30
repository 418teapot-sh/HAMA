import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../api/client'
import icMascot from '../assets/icons/ic_mascot.svg'
import Modal from '../components/Modal'
import NavigationBar from '../components/NavigationBar'
import Toast from '../components/Toast'
import { loadMe } from '../features/auth/api'
import {
  deleteGoal,
  getGoalReview,
  getGoals,
  getGoalTasks,
  getProgressSummary,
  replanGoal,
  type GoalSummary,
  type GoalTask,
} from '../features/goal/api'
import GoalMoreSheet from '../features/goal/components/GoalMoreSheet'
import ReplanScreen from '../features/goal/components/ReplanScreen'
import ReportGoalCard from '../features/goal/components/ReportGoalCard'
import { useAuthStore } from '../features/auth/store'
import CreditShortageModal from '../features/payment/CreditShortageModal'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

/** ENDED = 완료 처리한 목표(COMPLETED) + 종료일이 지난 목표(PAST) */
type Tab = 'IN_PROGRESS' | 'ENDED'

const TABS: { value: Tab; label: string }[] = [
  { value: 'IN_PROGRESS', label: '진행중인 목표' },
  { value: 'ENDED', label: '완료한 목표' },
]

type ReportItem = { goal: GoalSummary; tasks: GoalTask[]; aiSummary: string[] | null }

/**
 * 진행 중 목표의 AI 요약 (크레딧 안 씀). 상태 기록이 쌓이면 서버가 알아서 갱신합니다.
 * READY 면 요약 문장, 만드는 중·기록 없음은 안내 한 줄, 그 밖에는 숨김
 */
async function loadProgressSummary(goalId: number) {
  const summary = await getProgressSummary(goalId)
  if (summary.status === 'READY' && summary.summary) return summary.summary.split('\n').filter(Boolean)
  if (summary.status === 'PENDING' || summary.status === 'GENERATING') return ['AI 가 요약을 만들고 있어요.']
  if (summary.status === 'EMPTY') return ['완료한 투두에 상태를 남기면 AI 가 요약해드려요.']
  return null
}

/** 끝난 목표의 종료 AI 리뷰 두 줄 (성과 비교 · 칭찬/격려). 리뷰가 없으면(404) 숨김 */
async function loadReview(goalId: number) {
  const review = await getGoalReview(goalId)
  return [review.comparison, review.message]
}

/** 목표 목록과 각 목표의 할 일 · AI 요약을 함께 불러옵니다. 카드 하나가 실패해도 나머지는 보여 줍니다 */
async function loadGoals(tab: Tab): Promise<ReportItem[]> {
  const goals =
    tab === 'IN_PROGRESS'
      ? await getGoals('IN_PROGRESS')
      : (await Promise.all([getGoals('COMPLETED'), getGoals('PAST')])).flat()
  return Promise.all(
    goals.map(async (goal) => {
      const [goalTasks, aiSummary] = await Promise.all([
        getGoalTasks(goal.goalId).catch(() => null),
        (tab === 'IN_PROGRESS' ? loadProgressSummary(goal.goalId) : loadReview(goal.goalId)).catch(() => null),
      ])
      return { goal, tasks: goalTasks?.tasks ?? [], aiSummary }
    }),
  )
}

/**
 * 리포트 (Figma: 리포트)
 * - 화면 패딩 20 / 간격 20
 * - 탭 2개 (가로 간격 12): 175×95 / 패딩 8·20 / 간격 8 / 반경 20 / 테두리 1 Card_Back / Card_Shadow
 *   - 고른 탭: 배경 Brand/Main + 글자 Body/B5 White/W1
 *   - 안 고른 탭: 배경 Card_Back + 글자 Body/B5 Black/Gray40
 *   마스코트 아이콘은 흰색·회색 파일이 아직 없어 ic_mascot 에 색 필터를 씌웠습니다.
 *   TODO: Figma 탭 아이콘(흰색·회색)을 받아 파일로 바꿉니다
 * - 완료한 목표 탭 = 완료 처리한 목표(COMPLETED) 먼저, 그다음 종료일이 지난 목표(PAST)
 * - 목표 카드 목록 간격 20
 */
export default function ReportPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('IN_PROGRESS')
  const [items, setItems] = useState<ReportItem[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [moreGoal, setMoreGoal] = useState<GoalSummary | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<GoalSummary | null>(null)
  const [replanTarget, setReplanTarget] = useState<GoalSummary | null>(null)
  const [replanningId, setReplanningId] = useState<number | null>(null)
  const [creditShortage, setCreditShortage] = useState(false)
  const credits = useAuthStore((state) => state.user?.credits ?? 0)

  const reload = useCallback(async (target: Tab) => {
    setItems(null)
    setFailed(false)
    try {
      setItems(await loadGoals(target))
    } catch {
      setFailed(true)
      setItems([])
    }
  }, [])

  useEffect(() => {
    let ignore = false
    loadGoals(tab)
      .then((result) => {
        if (!ignore) setItems(result)
      })
      .catch(() => {
        if (!ignore) {
          setFailed(true)
          setItems([])
        }
      })
    return () => {
      ignore = true
    }
  }, [tab])

  const changeTab = (next: Tab) => {
    if (next === tab) return
    setItems(null)
    setFailed(false)
    setTab(next)
  }

  /** 일정 재계산 버튼. 크레딧이 0이면 부족 팝업, 있으면 확인 화면 (Figma 캡션) */
  const openReplan = async (goal: GoalSummary) => {
    // 저장된 크레딧이 0이면 결제 직후일 수 있어 다시 불러와 확인합니다
    if (useAuthStore.getState().user?.credits === 0) await loadMe()
    if (useAuthStore.getState().user?.credits === 0) setCreditShortage(true)
    else setReplanTarget(goal)
  }

  const handleReplan = async () => {
    if (!replanTarget) return
    const { goalId } = replanTarget
    setReplanTarget(null)
    setReplanningId(goalId)
    try {
      const result = await replanGoal(goalId)
      loadMe()
      toast.show(result.movedCount > 0 ? `투두 ${result.movedCount}개를 다시 배치했어요.` : '다시 배치할 투두가 없어요.')
    } catch (error) {
      // 일정 재계산은 크레딧 1회를 씀 (옮긴 투두가 없으면 차감 안 됨)
      if (getApiError(error)?.code === 'INSUFFICIENT_CREDITS') setCreditShortage(true)
      else toast.show('일정을 재계산하지 못했어요. 잠시 후 다시 시도해주세요.')
    } finally {
      setReplanningId(null)
    }
  }

  const handleDelete = async () => {
    if (!deleteTarget) return
    const goal = deleteTarget
    setDeleteTarget(null)
    try {
      await deleteGoal(goal.goalId)
      toast.show('목표를 삭제했어요.')
      reload(tab)
    } catch (error) {
      toast.show(
        getApiError(error)?.code === 'GOAL_NOT_DELETABLE'
          ? '진행 중인 목표는 삭제할 수 없어요.'
          : '목표를 삭제하지 못했어요. 잠시 후 다시 시도해주세요.',
      )
    }
  }

  return (
    <>
      <NavigationBar title="리포트" />

      <main className="flex flex-col gap-[20px] p-[20px]">
        <div className="flex gap-[12px]">
          {TABS.map(({ value, label }) => {
            const selected = value === tab
            return (
              <button
                key={value}
                type="button"
                aria-pressed={selected}
                onClick={() => changeTab(value)}
                className={`flex h-[95px] flex-1 flex-col items-center justify-center gap-[8px] rounded-[20px] border border-card-back px-[20px] py-[8px] text-body-b5 shadow-card-shadow ${
                  selected ? 'bg-brand-main text-white-w1' : 'bg-card-back text-black-gray40'
                }`}
              >
                <img
                  src={icMascot}
                  alt=""
                  className={`size-[36px] ${selected ? 'brightness-0 invert' : 'opacity-30 grayscale'}`}
                />
                {label}
              </button>
            )
          })}
        </div>

        {items === null ? (
          <p className="py-[40px] text-center text-body-b8 text-black-gray40">불러오는 중…</p>
        ) : failed ? (
          <p className="py-[40px] text-center text-body-b8 text-black-gray40">
            목표를 불러오지 못했어요. 잠시 후 다시 시도해주세요.
          </p>
        ) : items.length === 0 ? (
          <p className="py-[40px] text-center text-body-b8 text-black-gray40">
            {tab === 'IN_PROGRESS' ? '진행 중인 목표가 없어요.' : '완료한 목표가 없어요.'}
          </p>
        ) : (
          <ul className="flex flex-col gap-[20px]">
            {items.map(({ goal, tasks, aiSummary }) => (
              <li key={goal.goalId}>
                <ReportGoalCard
                  goal={goal}
                  tasks={tasks}
                  aiSummary={aiSummary}
                  onMore={() => setMoreGoal(goal)}
                  onReplan={goal.status === 'IN_PROGRESS' ? () => openReplan(goal) : undefined}
                  replanning={replanningId === goal.goalId}
                />
              </li>
            ))}
          </ul>
        )}
      </main>

      <GoalMoreSheet
        open={moreGoal !== null}
        isPast={moreGoal?.status === 'PAST' || moreGoal?.status === 'COMPLETED'}
        onClose={() => setMoreGoal(null)}
        onEdit={() => {
          // TODO: 목표 수정 화면 디자인이 정해지면 연결합니다 (PATCH /api/v1/goals/{goalId})
          setMoreGoal(null)
          toast.show('목표 수정은 곧 열릴 예정이에요.')
        }}
        onDelete={() => {
          setDeleteTarget(moreGoal)
          setMoreGoal(null)
        }}
        onRestart={() => navigate(paths.goalNew)}
      />

      <Modal
        open={deleteTarget !== null}
        title="목표를 삭제할까요?"
        description="목표와 연결된 투두도 함께 삭제돼요."
        confirmText="삭제하기"
        onCancel={() => setDeleteTarget(null)}
        onConfirm={handleDelete}
      />

      {replanTarget && (
        <ReplanScreen
          goal={replanTarget}
          credits={credits}
          onBack={() => setReplanTarget(null)}
          onConfirm={handleReplan}
        />
      )}

      <CreditShortageModal open={creditShortage} credits={credits} onClose={() => setCreditShortage(false)} />

      <Toast message={toast.message} />
    </>
  )
}
