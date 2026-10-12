import { ToastBubble } from '../../components/Toast'
import { formatKoreanDate } from '../../utils/date'

interface MoveDateHeaderProps {
  /** 옮길 날짜 (아직 고르지 않았으면 null) */
  target: Date | null
  onCancel: () => void
  onMove: () => void
}

/**
 * 투두 날짜 이동 화면 위쪽 (Figma 투두리스트_날짜 이동, 캘린더 위)
 * - 안내 말풍선 (Toast 모양): 고르기 전 "이동할 날짜를 선택해주세요." → 고른 뒤 그 날짜 "2026년 10월 16일 금"
 * - ButtonPair: 가로 간격 8, 두 버튼 높이 40 · 반경 8 · 글자 14px 행간 150%
 *   취소하기: 배경 White/W1 · 테두리 1px Black/Gray20 · 글자 700 Black/Gray60
 *   이동하기: 글자 600 White/W1, 배경 고르기 전 Black/Gray20(누를 수 없음) → 고른 뒤 Brand/Main
 */
export default function MoveDateHeader({ target, onCancel, onMove }: MoveDateHeaderProps) {
  return (
    <>
      <ToastBubble>{target ? formatKoreanDate(target) : '이동할 날짜를 선택해주세요.'}</ToastBubble>
      <div className="grid h-[40px] w-full grid-cols-2 gap-[8px]">
        <button
          type="button"
          onClick={onCancel}
          className="flex h-full items-center justify-center rounded-[8px] border border-black-gray20 bg-white-w1 text-[14px] leading-[150%] font-bold text-black-gray60"
        >
          취소하기
        </button>
        <button
          type="button"
          disabled={!target}
          onClick={onMove}
          className={`flex h-full items-center justify-center rounded-[8px] text-[14px] leading-[150%] font-semibold text-white-w1 ${
            target ? 'bg-brand-main' : 'bg-black-gray20'
          }`}
        >
          이동하기
        </button>
      </div>
    </>
  )
}
