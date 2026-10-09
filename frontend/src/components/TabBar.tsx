import icAdd from '../assets/icons/ic_add.svg'
import { paths } from '../paths'
import { HomeIcon, MyPageIcon, ReportIcon, TodoIcon } from './TabIcons'
import TabItem from './TabItem'

/**
 * 하단 탭바 (Figma 컴포넌트: TabBar_User, Design system 페이지)
 * - 세로 / 너비 402 / 높이 115(hug) / 위 반경 40·40 / 패딩 위 10·좌우 20·아래 0 / 간격 5
 * - 배경 Card_Back(#FCFCFC 75%) / 테두리 1px Stroke(#EEEEEE 60%, 안쪽)
 * - 효과 shadow(Y15 흐림40 #CECECE 8%) + 배경 흐림 20
 *   (Figma 배경 흐림 값은 CSS blur 의 2배라 backdrop-blur 10px 로 옮겼습니다)
 * - Frame 73: 가로 / 간격 12 / 가로·세로 가운데 정렬 → [홈·투두리스트(간격 5)] [+ 버튼] [리포트·마이페이지(간격 5)]
 *   (Figma 에서도 내용 374px 가 프레임 362px 보다 넓어 양옆으로 6px 씩 넘칩니다)
 * - Home Bar(402×32): 휴대폰이 그리는 영역이라 막대는 그리지 않고 높이만 비워 둡니다.
 */
export default function TabBar() {
  return (
    <nav
      aria-label="하단 메뉴"
      className="fixed bottom-0 left-1/2 flex w-full max-w-[402px] -translate-x-1/2 flex-col gap-[5px] rounded-t-[40px] bg-card-back px-[20px] pt-[10px] shadow-shadow ring-1 ring-stroke ring-inset backdrop-blur-[10px]"
    >
      <div className="flex w-full items-center justify-center gap-[12px]">
        <div className="flex shrink-0 gap-[5px]">
          <TabItem to={paths.home} label="홈" icon={HomeIcon} end />
          <TabItem to={paths.todos} label="투두리스트" icon={TodoIcon} />
        </div>

        {/*
          + 버튼 (Figma 컴포넌트: Frame 2085668201)
          68×68 고정 / 반경 100 / 배경 Brand/Main_20(#C2E8FF) / 테두리 1px Stroke(안쪽)
          효과 Card_Shadow(Y15 흐림48 #5480AE 8%) + 배경 흐림 20 / 아이콘 ic_add 48×48 가운데
          TODO: 눌렀을 때 이동할 화면 확인 필요 (목표 추가 화면 예정 · 상빈 담당)
        */}
        <button
          type="button"
          aria-label="추가"
          className="flex size-[68px] shrink-0 items-center justify-center rounded-[100px] bg-brand-main-20 shadow-card-shadow ring-1 ring-stroke ring-inset backdrop-blur-[10px]"
        >
          <img src={icAdd} alt="" className="size-[48px]" />
        </button>

        <div className="flex shrink-0 gap-[5px]">
          <TabItem to={paths.report} label="리포트" icon={ReportIcon} />
          <TabItem to={paths.mypage} label="마이페이지" icon={MyPageIcon} />
        </div>
      </div>

      {/* Home Bar 자리 (402×32) */}
      <div className="h-[32px] shrink-0" aria-hidden />
    </nav>
  )
}
