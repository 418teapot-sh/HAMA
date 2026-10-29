import { Link } from 'react-router'
import icCheck from '../../../assets/icons/ic_check.svg'
import icRightBlack from '../../../assets/icons/ic_right_black.svg'
import { paths } from '../../../paths'
import { formatGoalPeriod } from '../../../utils/date'

type GoalSummaryCardProps = {
  title: string
  /** 'YYYY-MM-DD' */
  startDate: string
  /** 'YYYY-MM-DD' */
  endDate: string
  /** 체크 표시와 함께 보여줄 할 일 (선택한 플랜의 반복 할 일) */
  items: string[]
  /** 연속 진행 일수. 백엔드에 아직 없어서 값이 없으면 뱃지를 숨김 */
  streakDays?: number
}

/**
 * 홈 목표 요약 카드 (Figma: 홈 > 목표 요약)
 * - 카드: 패딩 20·16 / 간격 16 / 반경 20 / 테두리 1 Card_Stroke_2 / 배경 Card_Back_2(40%) / Card_Shadow / 배경 흐림 10
 * - 위쪽 정보(왼쪽 여백 20, 간격 8)
 *   - 뱃지: 높이 20 / 좌우 패딩 20 / 반경 8·8·8·2 / 배경 Brand/Main_40 / 글자 Body/B9 White/W1
 *   - 제목 줄(양끝 정렬): 제목 Heading/H1 Black/Black + 오른쪽 화살표 24 Black/Black
 *   - 기간: Body/B8 Black/Gray60
 * - 아래 목록 상자: 패딩 20 / 반경 16 / 테두리 1 Stroke / 배경 Card_Back / Card_Shadow / 배경 흐림 10
 *   - 줄 간격 4, 줄마다 체크 아이콘 24 + 간격 8 + 글자 Body/B7 SemiBold Brand/Main_80
 *     (이 화면의 B7 은 SemiBold 라서 토큰 굵기(400) 대신 font-semibold 로 덮어씀)
 */
export default function GoalSummaryCard({ title, startDate, endDate, items, streakDays }: GoalSummaryCardProps) {
  return (
    <div className="flex w-full flex-col gap-[16px] rounded-[20px] border border-card-stroke-2 bg-card-back-2/40 px-[16px] py-[20px] shadow-card-shadow backdrop-blur-[10px]">
      <div className="flex flex-col items-start gap-[8px] pl-[20px]">
        {streakDays !== undefined && (
          <span className="flex h-[20px] items-center rounded-[8px] rounded-bl-[2px] bg-brand-main-40 px-[20px] text-body-b9 text-white-w1">
            연속 {streakDays}일째 진행중!
          </span>
        )}

        <Link to={paths.report} className="flex w-full items-center justify-between">
          <span className="text-heading-h1 text-black-black">{title}</span>
          <img src={icRightBlack} alt="" className="size-[24px] shrink-0" />
        </Link>

        <p className="text-body-b8 text-black-gray60">{formatGoalPeriod(startDate, endDate)}</p>
      </div>

      {items.length > 0 && (
        <ul className="flex flex-col gap-[4px] rounded-[16px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]">
          {items.map((item) => (
            <li key={item} className="flex items-center gap-[8px]">
              <img src={icCheck} alt="" className="size-[24px] shrink-0" />
              <span className="text-body-b7 font-semibold text-brand-main-80">{item}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
