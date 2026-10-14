import { toDateKey } from '../../utils/date'
import type { GoalTree } from './api'

/** 오늘이 기간 안에 있는 기간 목표(월/주) 제목들 */
export function getCurrentPeriodGoals(tree: GoalTree | null) {
  if (!tree) return []
  const today = toDateKey(new Date())
  return tree.milestones
    .flatMap((milestone) => milestone.periodGoals)
    .filter((periodGoal) => periodGoal.startDate <= today && today <= periodGoal.endDate)
    .map((periodGoal) => periodGoal.title)
}
