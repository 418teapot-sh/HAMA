import icBack from '../../../assets/icons/ic_back.svg'
import Button from '../../../components/Button'
import NavigationBar from '../../../components/NavigationBar'
import { formatGoalPeriod } from '../../../utils/date'
import type { GoalSummary } from '../api'
import GoalHeader from './GoalHeader'

type ReplanScreenProps = {
  goal: GoalSummary
  /** 남은 크레딧 */
  credits: number
  onBack: () => void
  onConfirm: () => void
}

/**
 * 일정 재계산 확인 화면 (Figma: 일정 재계산 · 차감 안내)
 * 탭바까지 덮는 전체 화면입니다.
 * - 화면 패딩 20·20·0·20 / 머리말 ↔ 카드 간격 20 / 카드 사이 간격 12
 * - 머리말 아래 말풍선: 패딩 4·12 / 반경 12·12·12·2 / Brand/Main_40 / Body/B9 White/W1
 * - 목표 카드: 패딩 20 / 반경 20 / Card_Back / 테두리 Stroke / Card_Shadow
 *   제목 18 SemiBold Brand/Main_80, 목표 Heading/H1 Black/Black, 기간 Body/B8 Black/Gray60
 * - 안내 카드: 패딩 20 / 반경 20 / Card_Back_2 40% / 테두리 Card_Back_2 30%
 *   제목 Body/B7 SemiBold Brand/Main_80, 설명 Body/B8 Black/Gray60
 * - 아래: 패딩 20 / 간격 10. 차감 안내 Body/B7 SemiBold Brand/Main, 남은 크레딧 12px Brand/EO, 공용 Button
 */
export default function ReplanScreen({ goal, credits, onBack, onConfirm }: ReplanScreenProps) {
  return (
    <div className="fixed inset-y-0 left-1/2 z-50 flex w-full max-w-[402px] -translate-x-1/2 flex-col bg-white-w1">
      <NavigationBar
        title="일정 재계산"
        leftIcon={<img src={icBack} alt="" className="size-[24px]" />}
        leftLabel="뒤로"
        onLeftClick={onBack}
      />

      <main className="flex flex-1 flex-col gap-[20px] overflow-y-auto px-[20px] pt-[20px]">
        <div className="flex flex-col items-start gap-[8px]">
          <GoalHeader title={'밀린 일정,\n다시 맞춰볼까요?'} />
          <span className="rounded-[12px] rounded-bl-[2px] bg-brand-main-40 px-[12px] py-[4px] text-body-b9 text-white-w1">
            남은 기간에 맞춰 할 일을 다시 재배치 해요
          </span>
        </div>

        <div className="flex flex-col gap-[12px]">
          <section className="flex flex-col gap-[12px] rounded-[20px] border border-stroke bg-card-back p-[20px] shadow-card-shadow backdrop-blur-[10px]">
            <h2 className="text-[18px] font-semibold text-brand-main-80">진행 중인 목표</h2>
            <p className="text-heading-h1 text-black-black">{goal.title}</p>
            <p className="text-body-b8 text-black-gray60">{formatGoalPeriod(goal.startDate, goal.endDate)}</p>
          </section>

          <section className="flex flex-col gap-[12px] rounded-[20px] border border-card-back-2/30 bg-card-back-2/40 p-[20px] shadow-card-shadow backdrop-blur-[10px]">
            <h2 className="text-body-b7 font-semibold text-brand-main-80">남은 할 일을 일정에 다시 배치해요.</h2>
            <p className="text-body-b8 whitespace-pre-line text-black-gray60">
              {'목표와 종료일을 기준으로\n새로운 실행 계획을 제안해 드려요.'}
            </p>
          </section>
        </div>
      </main>

      <div className="flex flex-col gap-[10px] p-[20px]">
        <div className="flex flex-col gap-[2px]">
          <p className="text-body-b7 font-semibold text-brand-main">일정 재계산 시 크레딧 1회가 차감됩니다.</p>
          <p className="text-[12px] leading-[130%] text-brand-eo">남은 크레딧 {credits}회</p>
        </div>
        <Button onClick={onConfirm}>일정 재계산하기</Button>
      </div>
    </div>
  )
}
