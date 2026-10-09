import type { ComponentType } from 'react'
import { NavLink } from 'react-router'

interface TabItemProps {
  to: string
  label: string
  icon: ComponentType<{ className?: string }>
  /** 홈('/')처럼 하위 경로에서까지 선택되면 안 되는 탭에 씁니다 */
  end?: boolean
}

/**
 * 하단 탭 하나 (Figma 컴포넌트: Tap)
 * - 세로 / 68×48 고정 / 간격 4 / 가운데 정렬
 * - 아이콘 24×24: 선택 #042558(Brand/Main), 미선택 #A2A2A2(Black/Gray40)
 * - 이름: Body/B10 (12/400/160%, 자간 -1.5%) Black/Gray80, 선택 여부와 상관없이 같음
 */
export default function TabItem({ to, label, icon: Icon, end }: TabItemProps) {
  return (
    <NavLink to={to} end={end} className="flex h-[48px] w-[68px] shrink-0 flex-col items-center justify-center gap-[4px]">
      {({ isActive }) => (
        <>
          <Icon className={`size-[24px] ${isActive ? 'text-brand-main' : 'text-black-gray40'}`} />
          <span className="flex w-full justify-center text-body-b10 text-black-gray80">{label}</span>
        </>
      )}
    </NavLink>
  )
}
