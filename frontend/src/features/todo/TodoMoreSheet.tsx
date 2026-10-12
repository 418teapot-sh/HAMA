import type { ReactNode } from 'react'
import BottomSheet from '../../components/BottomSheet'
import { ForwardArrowIcon, PenIcon, TrashIcon } from '../../components/TodoIcons'

interface TodoMoreSheetProps {
  open: boolean
  onEdit: () => void
  onDelete: () => void
  onMoveDate: () => void
  onClose: () => void
}

/**
 * 투두 더보기 시트 (Figma 투두리스트 더보기 > Frame 2085668231)
 * 버튼 묶음 (간격 12): [수정하기 | 삭제하기] (가로 간격 12, 높이 96) / 날짜 변경 (높이 75)
 * 시트 모양과 닫기 버튼은 공용 BottomSheet 를 씁니다.
 */
export default function TodoMoreSheet({ open, onEdit, onDelete, onMoveDate, onClose }: TodoMoreSheetProps) {
  return (
    <BottomSheet open={open} onClose={onClose}>
      <div className="flex w-full flex-col gap-[12px]">
        <div className="flex h-[96px] w-full items-center gap-[12px]">
          <SheetAction icon={<PenIcon className="size-[24px]" />} label="수정하기" bgClass="bg-category-schedule" onClick={onEdit} />
          <SheetAction icon={<TrashIcon className="size-[24px]" />} label="삭제하기" bgClass="bg-category-task" onClick={onDelete} />
        </div>
        <div className="flex h-[75px] w-full items-center">
          <SheetAction icon={<ForwardArrowIcon className="size-[24px]" />} label="날짜 변경" bgClass="bg-category-etc" onClick={onMoveDate} />
        </div>
      </div>
    </BottomSheet>
  )
}

/**
 * 시트의 색 버튼 (Figma Frame 2085668222 · 2085668225)
 * 세로 가운데 / 패딩 12·16 / 간격 6 / 반경 12 / 높이는 줄을 가득 채움
 * 아이콘 24 · 글자 Body/B5 White/W1
 */
function SheetAction({ icon, label, bgClass, onClick }: { icon: ReactNode; label: string; bgClass: string; onClick: () => void }) {
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
