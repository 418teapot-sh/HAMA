import type { ReactNode } from 'react'

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
