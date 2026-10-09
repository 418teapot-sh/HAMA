/**
 * 하단 토스트 (Figma 컴포넌트: Toast, 로그인 오류 화면에서 확인)
 * 반경 100 / 패딩 8·20 / 배경 Card_Back_2 #C2E8FF 40% / 글자 Body/B6 Brand/Main 가운데 정렬
 * 위치: 화면 가로 가운데, 토스트 아래 끝이 화면 아래에서 72px
 *       (Figma 프레임 874px 기준: 감싼 프레임 top 762 + 높이 80, 아래 패딩 40)
 */
export default function Toast({ message }: { message: string | null }) {
  if (!message) return null

  return (
    <div role="status" aria-live="polite" className="pointer-events-none fixed inset-x-0 bottom-[72px] flex justify-center">
      <p className="flex items-center gap-[10px] rounded-[100px] bg-card-back-2/40 px-[20px] py-[8px] text-center text-body-b6 text-brand-main">
        {message}
      </p>
    </div>
  )
}
