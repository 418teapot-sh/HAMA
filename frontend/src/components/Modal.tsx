import type { ReactNode } from 'react'

type ModalProps = {
  open: boolean
  title: ReactNode
  description?: ReactNode
  cancelText?: string
  confirmText: string
  onCancel: () => void
  onConfirm: () => void
}

export default function Modal({
  open,
  title,
  description,
  cancelText = '닫기',
  confirmText,
  onCancel,
  onConfirm,
}: ModalProps) {
  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black-gray80/90 px-[20px]"
      onClick={onCancel}
    >
      <div
        role="dialog"
        aria-modal="true"
        className="flex w-full max-w-[362px] flex-col gap-[20px] rounded-[12px] bg-white-w1 px-[20px] py-[24px]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex flex-col items-center gap-[8px] text-center">
          <p className="text-heading-h6 font-semibold text-black-black">{title}</p>
          {description && <p className="text-body-b8 text-black-gray60">{description}</p>}
        </div>

        <div className="flex gap-[8px]">
          <button
            type="button"
            className="h-[40px] flex-1 rounded-[8px] border border-black-gray20 bg-white-w1 text-body-b8 font-bold text-black-gray60"
            onClick={onCancel}
          >
            {cancelText}
          </button>
          <button
            type="button"
            className="h-[40px] flex-1 rounded-[8px] bg-brand-main text-body-b7 font-semibold text-white-w1"
            onClick={onConfirm}
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  )
}
