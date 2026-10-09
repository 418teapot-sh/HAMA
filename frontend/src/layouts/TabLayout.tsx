import { Outlet } from 'react-router'
import TabBar from '../components/TabBar'

/** 하단 탭바 높이 (Figma TabBar_User 115px). 화면 내용이 탭바에 가려지지 않도록 아래를 이만큼 비웁니다. */
const TAB_BAR_HEIGHT = 'pb-[115px]'

/** 하단 탭바가 있는 화면(홈 · 투두리스트 · 리포트 · 마이페이지)의 레이아웃 */
export default function TabLayout() {
  return (
    <div className={`flex w-full flex-1 flex-col ${TAB_BAR_HEIGHT}`}>
      <Outlet />
      <TabBar />
    </div>
  )
}
