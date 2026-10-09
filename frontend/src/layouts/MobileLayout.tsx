import { Outlet } from 'react-router'

/**
 * 모바일 화면 레이아웃. Figma 프레임 너비 402px 을 최대 너비로 두고 가운데 정렬합니다.
 * (Figma 의 StatusBar·Home Bar 는 휴대폰이 직접 그리는 영역이라 웹에서는 그리지 않습니다)
 */
export default function MobileLayout() {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[402px] flex-col bg-white-w1">
      <Outlet />
    </div>
  )
}
