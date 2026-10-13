import type { ReactNode } from 'react'
import BottomSheet from '../../../components/BottomSheet'
import { PenIcon, SparkleIcon, TrashIcon } from '../../../components/TodoIcons'

type GoalMoreSheetProps = {
  open: boolean
  /** 지난 목표면 '다시 시작하기' 버튼이 추가됨 */
  isPast: boolean
  onEdit: () => void
  onDelete: () => void
  onRestart: () => void
  onClose: () => void
}

/**
 * 리포트 목표 더보기 시트 (Figma: 리포트 더보기-진행중 / 리포트 더보기 - 완료)
 * - [수정하기 | 삭제하기] 가로 간격 12, 높이 96 (투두 더보기 시트와 같은 버튼 모양)
 * - 지난 목표: 아래에 다시 시작하기 (높이 75, Brand/Main)
 */
export default function GoalMoreSheet({ open, isPast, onEdit, onDelete, onRestart, onClose }: GoalMoreSheetProps) {
  return (
    <BottomSheet open={open} onClose={onClose}>
      <div className="flex w-full flex-col gap-[12px]">
        <div className="flex h-[96px] w-full items-center gap-[12px]">
          <SheetAction icon={<PenIcon className="size-[24px]" />} label="수정하기" bgClass="bg-category-schedule" onClick={onEdit} />
          <SheetAction icon={<TrashIcon className="size-[24px]" />} label="삭제하기" bgClass="bg-category-task" onClick={onDelete} />
        </div>
        {isPast && (
          <div className="flex h-[75px] w-full items-center">
            <SheetAction icon={<SparkleIcon className="size-[24px]" />} label="다시 시작하기" bgClass="bg-brand-main" onClick={onRestart} />
          </div>
        )}
      </div>
    </BottomSheet>
  )
}

/** 시트의 색 버튼: 세로 가운데 / 패딩 12·16 / 간격 6 / 반경 12 / 아이콘 24 · Body/B5 White/W1 */
function SheetAction({
  icon,
  label,
  bgClass,
  onClick,
}: {
  icon: ReactNode
  label: string
  bgClass: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex h-full flex-1 flex-col items-center justify-center gap-[6px] rounded-[12px] px-[16px] py-[12px] text-body-b5 text-white-w1 ${bgClass}`}
    >
      {icon}
      {label}
    </button>
  )
}
