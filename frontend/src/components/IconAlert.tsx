import type { ReactNode } from 'react'

interface IconAlertProps {
  open: boolean
  /** 동그라미 안 아이콘 (24×24, 흰색으로 그려짐) */
  icon: ReactNode
  title: string
  /** 안내 문구. 줄마다 따로 넘깁니다 (Figma 줄바꿈 그대로) */
  lines: string[]
  /** 강조 한 줄 (예: 남은 크레딧 5회). 색은 noteClass 로 정합니다 */
  note?: string
  noteClass?: string
  confirmText: string
  onConfirm: () => void
  /** 왼쪽 흰 버튼. 없으면 확인 버튼 하나만 가득 채웁니다 (충전 성공) */
  cancelText?: string
  onCancel?: () => void
  /** 왼쪽 버튼 너비. 기본은 두 버튼 같은 너비, 크레딧 부족 팝업은 136 고정 */
  cancelClassName?: string
}

/**
 * 아이콘 알림 팝업 (Figma 컴포넌트: Alert2 — 결제 · 크레딧 화면에서 확인)
 * - 뒷배경: 화면 전체 Black/Gray80 90% (공용 Modal 과 같음)
 * - 상자: 너비 362 / 패딩 24·20 / 반경 12 / White/W1, 화면 가운데
 * - 안쪽 세로 간격 20: [내용 (가운데 · 간격 8)] [버튼 줄]
 *   내용: 동그라미 36 Brand/Main + 아이콘 24 White/W1
 *        → 제목 Heading/H6 18px 600 행간 21px Black/Black 와 안내 문구 Body/B8 Black/Gray60 (사이 8)
 *        → 강조 한 줄 Body/B7 SemiBold
 * - 버튼 줄 (간격 8, 높이 40, 반경 8):
 *   왼쪽 White/W1 · 테두리 1px Black/Gray20 · Body/B8 Black/Gray60 / 오른쪽 Brand/Main · Body/B7 SemiBold White/W1
 * 뒷배경을 누르면 왼쪽 버튼(없으면 확인 버튼)과 같이 닫힙니다.
 */
export default function IconAlert({
  open,
  icon,
  title,
  lines,
  note,
  noteClass = 'text-brand-main-80',
  confirmText,
  onConfirm,
  cancelText,
  onCancel,
  cancelClassName = 'flex-1',
}: IconAlertProps) {
  if (!open) return null

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black-gray80/90 px-[20px]"
      onClick={onCancel ?? onConfirm}
    >
      <div
        role="alertdialog"
        aria-modal="true"
        aria-label={title}
        className="flex w-full max-w-[362px] flex-col gap-[20px] rounded-[12px] bg-white-w1 px-[20px] py-[24px]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex flex-col items-center gap-[8px] text-center">
          <span className="flex size-[36px] items-center justify-center rounded-full bg-brand-main text-white-w1">{icon}</span>
          <div className="flex flex-col items-center gap-[8px]">
            <p className="text-[18px] leading-[21px] font-semibold text-black-black">{title}</p>
            <p className="text-body-b8 text-black-gray60">
              {lines.map((line) => (
                <span key={line} className="block">
                  {line}
                </span>
              ))}
            </p>
          </div>
          {note && <p className={`text-body-b7 font-semibold ${noteClass}`}>{note}</p>}
        </div>

        <div className="flex h-[40px] w-full gap-[8px]">
          {cancelText && (
            <button
              type="button"
              onClick={onCancel}
              className={`h-full rounded-[8px] border border-black-gray20 bg-white-w1 text-body-b8 text-black-gray60 ${cancelClassName}`}
            >
              {cancelText}
            </button>
          )}
          <button
            type="button"
            onClick={onConfirm}
            className="h-full flex-1 rounded-[8px] bg-brand-main text-body-b7 font-semibold text-white-w1"
          >
            {confirmText}
          </button>
        </div>
      </div>
    </div>
  )
}
