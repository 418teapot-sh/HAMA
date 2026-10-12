import type { ReactNode } from 'react'

/**
 * 토스트 말풍선 (Figma 컴포넌트: Toast)
 * 반경 100 / 패딩 8·20 / 배경 Card_Back_2 #C2E8FF 40% / 글자 Body/B6 Brand/Main 가운데 정렬
 * 잠깐 떴다 사라지는 토스트 말고, 투두 날짜 이동 화면 위쪽 안내처럼 계속 보이는 곳에도 같은 모양으로 씁니다.
 */
export function ToastBubble({ children }: { children: ReactNode }) {
  return (
    <p className="flex items-center gap-[10px] rounded-[100px] bg-card-back-2/40 px-[20px] py-[8px] text-center text-body-b6 text-brand-main">
      {children}
    </p>
  )
}

interface ToastProps {
  message: string | null
  /**
   * 위치를 화면 아래가 아니라 감싼 요소(position: relative) 위쪽에서 이만큼(px) 내려온 곳으로 정할 때.
   * 예) 투두리스트 화면: 캘린더 카드 아래쪽 (Figma 투두리스트_삭제 알림 · 날짜 이동 top 415)
   */
  top?: number
}

/**
 * 토스트 (Figma 컴포넌트: Toast, 로그인 오류 화면에서 확인)
 * 기본 위치: 화면 가로 가운데, 토스트 아래 끝이 화면 아래에서 72px
 *       (Figma 프레임 874px 기준: 감싼 프레임 top 762 + 높이 80, 아래 패딩 40)
 * top 을 주면: 감싼 요소 안에서 가로 가운데, 위에서 top px
 */
export default function Toast({ message, top }: ToastProps) {
  if (!message) return null

  const position = top === undefined ? 'fixed bottom-[72px]' : 'absolute z-10'

  return (
    <div
      role="status"
      aria-live="polite"
      style={top === undefined ? undefined : { top }}
      className={`pointer-events-none inset-x-0 flex justify-center ${position}`}
    >
      <ToastBubble>{message}</ToastBubble>
    </div>
  )
}
