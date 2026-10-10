import type { ReactNode } from 'react'

/**
 * 공용 바텀시트 (Figma: 투두리스트 더보기 화면의 팝업 + 시트)
 * - 뒷배경: 화면 전체 Black/Gray80 90% (Modal 과 같음)
 * - 시트: 화면 아래 붙음 / 위쪽 반경 20 / 테두리 1 Stroke / 배경 White/W1
 * - 손잡이: 44×5 Black/Gray10, 위 여백 20
 * - 손잡이 ↔ 내용 간격 24, 내용 너비 360(좌우 여백 20), 내용 ↔ 닫기 버튼 간격 24
 * - 닫기 버튼: 높이 48 / 반경 12 / 패딩 12·20 / 테두리 Black/Gray20 / 글자 Body/B3 Black/Gray60
 * - 아래 여백 32 (Figma Home Bar 영역)
 */

type BottomSheetProps = {
  open: boolean
  onClose: () => void
  closeText?: string
  children: ReactNode
}

export default function BottomSheet({ open, onClose, closeText = '닫기', children }: BottomSheetProps) {
  if (!open) return null

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black-gray80/90" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        className="flex w-full max-w-[402px] flex-col items-center gap-[24px] rounded-t-[20px] border border-stroke bg-white-w1 pb-[32px]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="pt-[20px]">
          <div className="h-[5px] w-[44px] rounded-full bg-black-gray10" />
        </div>

        <div className="flex w-full flex-col gap-[24px] px-[20px]">
          {children}
          <button
            type="button"
            className="h-[48px] w-full rounded-[12px] border border-black-gray20 bg-white-w1 px-[20px] py-[12px] text-body-b3 text-black-gray60"
            onClick={onClose}
          >
            {closeText}
          </button>
        </div>
      </div>
    </div>
  )
}
