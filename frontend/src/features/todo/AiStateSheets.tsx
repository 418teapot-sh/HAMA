import { useState, type FormEvent } from 'react'
import BottomSheet from '../../components/BottomSheet'
import Modal from '../../components/Modal'
import { ToastBubble } from '../../components/Toast'
import { SparkleIcon } from '../../components/TodoIcons'
import type { GoalSummary } from '../goal/api'

/**
 * AI 목표 투두를 완료했을 때 뜨는 팝업 (Figma AI 목표 현재 상태 입력 흐름 170:15254)
 * "AI 목표를 완료 했어요! 현재 상태를 입력할까요?" + 안내 + [닫기 | 상태 입력하기]
 */
export function AiStatePromptModal({ open, onClose, onConfirm }: { open: boolean; onClose: () => void; onConfirm: () => void }) {
  return (
    <Modal
      open={open}
      title={
        <>
          AI 목표를 완료 했어요!
          <br />
          현재 상태를 입력할까요?
        </>
      }
      description={
        <>
          이후에도 별 아이콘(
          <SparkleIcon className="inline size-[14px] align-[-2px] text-brand-main" />
          )을 누르면
          <br />
          상태 입력이 가능합니다.
        </>
      }
      confirmText="상태 입력하기"
      onCancel={onClose}
      onConfirm={onConfirm}
    />
  )
}

/**
 * AI 가 만든 투두의 sparkle 을 눌렀을 때 시트 (Figma 투두리스트 더보기 170:18584)
 * [✦ AI 목표 상태수정] (Brand/Main, 반경 12) + "AI 목표는 수정 및 삭제가 불가능합니다." + 닫기
 */
export function AiStateSheet({ open, onClose, onEditState }: { open: boolean; onClose: () => void; onEditState: () => void }) {
  return (
    <BottomSheet open={open} onClose={onClose}>
      <div className="flex w-full flex-col items-center gap-[12px]">
        <button
          type="button"
          onClick={onEditState}
          className="flex h-[96px] w-full flex-col items-center justify-center gap-[6px] rounded-[12px] bg-brand-main px-[16px] py-[12px] text-body-b5 text-white-w1"
        >
          <SparkleIcon className="size-[24px]" />
          <span className="text-center">
            AI 목표
            <br />
            상태수정
          </span>
        </button>
        <p className="text-body-b8 text-black-gray40">AI 목표는 수정 및 삭제가 불가능합니다.</p>
      </div>
    </BottomSheet>
  )
}

/**
 * 현재 상태 입력창 (Figma AI 목표 현재 상태 입력 중 170:16270)
 * 테두리 1px Brand/Main · 반경 8 / "현재 상태를 입력해주세요. (예: 토익 700점)" / 오른쪽 sparkle 버튼
 * 이미 입력한 상태가 있으면 그 내용으로 시작합니다 (상태수정).
 */
export function AiStateInput({ initial, busy, onSubmit }: { initial: string; busy: boolean; onSubmit: (note: string) => void }) {
  const [note, setNote] = useState(initial)

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (busy) return
    onSubmit(note.trim())
  }

  return (
    <>
      <div className="flex h-[12px] w-full justify-center" aria-hidden>
        <svg width="17" height="13" viewBox="0 0 17 13" fill="none" className="text-brand-main">
          <path
            d="M6.74762 0.779922C7.54822 -0.260093 9.11664 -0.260093 9.91724 0.779922L16.246 9.0012C17.2584 10.3163 16.3208 12.2212 14.6612 12.2212H2.00368C0.344007 12.2212 -0.593522 10.3163 0.418872 9.0012L6.74762 0.779922Z"
            fill="currentColor"
          />
        </svg>
      </div>
      <form
        onSubmit={handleSubmit}
        className="flex h-[48px] w-full items-start justify-between gap-[10px] rounded-[8px] border border-brand-main bg-white-w1 px-[20px] py-[12px]"
      >
        <input
          autoFocus
          value={note}
          maxLength={500}
          placeholder="현재 상태를 입력해주세요. (예: 토익 700점)"
          aria-label="현재 상태"
          onChange={(e) => setNote(e.target.value)}
          className="h-[24px] min-w-0 flex-1 text-body-b6 text-black-gray80 outline-none placeholder:text-black-gray40"
        />
        <button type="submit" aria-label="현재 상태 저장" disabled={busy}>
          <SparkleIcon className="size-[24px] text-brand-main" />
        </button>
      </form>
    </>
  )
}

/**
 * AI 칸 + 를 눌렀을 때 목표 고르기 시트 (Figma AI 목표 추가 728:9177)
 * 위에 "할일을 추가할 목표를 선택해주세요" 말풍선, 진행 중인 목표 목록(체크 + 이름), [닫기 | 확인]
 */
export function GoalPickSheet({
  open,
  goals,
  onClose,
  onPick,
}: {
  open: boolean
  goals: GoalSummary[]
  onClose: () => void
  onPick: (goal: GoalSummary) => void
}) {
  const [pickedId, setPickedId] = useState<number | null>(null)
  if (!open) return null
  const picked = goals.find((goal) => goal.goalId === pickedId) ?? null

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black-gray80/90" onClick={onClose}>
      <div
        role="dialog"
        aria-modal="true"
        className="flex w-full max-w-[402px] flex-col items-center gap-[20px] rounded-t-[20px] border border-stroke bg-white-w1 pb-[32px]"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="pt-[20px]">
          <div className="h-[5px] w-[44px] rounded-full bg-black-gray10" />
        </div>
        <ToastBubble>할일을 추가할 목표를 선택해주세요</ToastBubble>
        <ul className="flex max-h-[240px] w-full flex-col gap-[4px] overflow-y-auto px-[20px]">
          {goals.length === 0 && <li className="py-[12px] text-center text-body-b7 text-black-gray40">진행 중인 AI 목표가 없어요.</li>}
          {goals.map((goal) => {
            const checked = goal.goalId === pickedId
            return (
              <li key={goal.goalId}>
                <button
                  type="button"
                  role="radio"
                  aria-checked={checked}
                  onClick={() => setPickedId(goal.goalId)}
                  className="flex h-[44px] w-full items-center gap-[8px] border-b border-black-gray10 text-left"
                >
                  <span
                    className={`flex size-[20px] shrink-0 items-center justify-center rounded-[4px] border ${
                      checked ? 'border-brand-main bg-brand-main' : 'border-black-gray40 bg-white-w1'
                    }`}
                  >
                    {checked && (
                      <svg width="12" height="10" viewBox="0 0 12 10" fill="none" aria-hidden>
                        <path d="M1 5L4.5 8.5L11 1.5" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
                      </svg>
                    )}
                  </span>
                  <span className="min-w-0 flex-1 truncate text-body-b7 font-semibold text-black-black">{goal.title}</span>
                </button>
              </li>
            )
          })}
        </ul>
        <div className="flex w-full gap-[8px] px-[20px]">
          <button
            type="button"
            onClick={onClose}
            className="h-[48px] flex-1 rounded-[12px] border border-black-gray20 bg-white-w1 text-body-b3 text-black-gray60"
          >
            닫기
          </button>
          <button
            type="button"
            disabled={!picked}
            onClick={() => picked && onPick(picked)}
            className={`h-[48px] flex-1 rounded-[12px] text-body-b3 text-white-w1 ${picked ? 'bg-brand-main' : 'bg-black-gray20'}`}
          >
            확인
          </button>
        </div>
      </div>
    </div>
  )
}
