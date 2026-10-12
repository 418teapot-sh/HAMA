import type { ReactNode } from 'react'

/**
 * 투두 목록 카드 (Figma 투두리스트 > Frame 2085668203)
 * 세로 / 패딩 20 / 간격 8 / 반경 20 / 배경 Card_Back / 테두리 1px Stroke / Card_Shadow + 배경 흐림 10
 * faded: 날짜 이동 중 나머지 투두 (Figma 투두리스트_날짜 이동 Frame 2085668204, 불투명도 30%) — 누를 수 없음
 */
export default function TodoCard({ children, faded = false }: { children: ReactNode; faded?: boolean }) {
  return (
    <ul
      inert={faded}
      className={`flex w-full flex-col gap-[8px] rounded-[20px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px] ${
        faded ? 'opacity-30' : ''
      }`}
    >
      {children}
    </ul>
  )
}

/**
 * 투두가 없는 날 (Figma 투두리스트_투두리스트없을때)
 * 같은 카드 안에 가운데 정렬 한 줄 (높이 21): "아직 투두리스트가 없어요." Body/B7 SemiBold Black/Gray40
 */
export function TodoEmpty() {
  return (
    <TodoCard>
      <li className="flex h-[21px] w-full items-center justify-center text-body-b7 font-semibold text-black-gray40">
        아직 투두리스트가 없어요.
      </li>
    </TodoCard>
  )
}
