import { useCallback, useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../api/client'
import icMascot from '../assets/icons/ic_mascot.svg'
import Modal from '../components/Modal'
import NavigationBar from '../components/NavigationBar'
import Toast from '../components/Toast'
import { deleteGoal, getGoals, getGoalTree, replanGoal, type GoalSummary, type GoalTree } from '../features/goal/api'
import GoalMoreSheet from '../features/goal/components/GoalMoreSheet'
import ReportGoalCard from '../features/goal/components/ReportGoalCard'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

type Tab = 'IN_PROGRESS' | 'PAST'

const TABS: { value: Tab; label: string }[] = [
  { value: 'IN_PROGRESS', label: '진행중인 목표' },
  { value: 'PAST', label: '완료한 목표' },
]

type GoalWithTree = { goal: GoalSummary; tree: GoalTree | null }

/** 목표 목록과 각 목표의 계층 트리(체크 목록·AI 요약용)를 함께 불러옵니다 */
async function loadGoals(tab: Tab): Promise<GoalWithTree[]> {
  const goals = await getGoals(tab)
  const trees = await Promise.all(goals.map((goal) => getGoalTree(goal.goalId).catch(() => null)))
  return goals.map((goal, i) => ({ goal, tree: trees[i] }))
}

/**
 * 리포트 (Figma: 리포트)
 * - 화면 패딩 20 / 간격 20
 * - 탭 2개 (가로 간격 12): 175×95 / 패딩 8·20 / 간격 8 / 반경 20 / 테두리 1 Card_Back / Card_Shadow
 *   - 고른 탭: 배경 Brand/Main + 글자 Body/B5 White/W1
 *   - 안 고른 탭: 배경 Card_Back + 글자 Body/B5 Black/Gray40
 *   마스코트 아이콘은 흰색·회색 파일이 아직 없어 ic_mascot 에 색 필터를 씌웠습니다.
 *   TODO: Figma 탭 아이콘(흰색·회색)을 받아 파일로 바꿉니다
 * - 목표 카드 목록 간격 20
 */
export default function ReportPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [tab, setTab] = useState<Tab>('IN_PROGRESS')
  const [items, setItems] = useState<GoalWithTree[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [moreGoal, setMoreGoal] = useState<GoalSummary | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<GoalSummary | null>(null)
  const [replanningId, setReplanningId] = useState<number | null>(null)

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

  const handleReplan = async (goalId: number) => {
    setReplanningId(goalId)
    try {
      const result = await replanGoal(goalId)
      toast.show(result.movedCount > 0 ? `투두 ${result.movedCount}개를 다시 배치했어요.` : '다시 배치할 투두가 없어요.')
    } catch {
      toast.show('일정을 재계산하지 못했어요. 잠시 후 다시 시도해주세요.')
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
            {items.map(({ goal, tree }) => (
              <li key={goal.goalId}>
                <ReportGoalCard
                  goal={goal}
                  tree={tree}
                  onMore={() => setMoreGoal(goal)}
                  onReplan={goal.status === 'IN_PROGRESS' ? () => handleReplan(goal.goalId) : undefined}
                  replanning={replanningId === goal.goalId}
                />
              </li>
            ))}
          </ul>
        )}
      </main>

      <GoalMoreSheet
        open={moreGoal !== null}
        isPast={moreGoal?.status === 'PAST'}
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

      <Toast message={toast.message} />
    </>
  )
}
