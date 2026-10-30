import icMascot from '../../../assets/icons/ic_mascot.svg'
import icSparkleMain from '../../../assets/icons/ic_sparkle_main.svg'

/** 머리말: [마스코트 36 + 반짝이 24, 간격 8] ↔ 제목 간격 8. 제목은 Heading/H1 + 그라데이션 */
export default function GoalHeader({ title, center = false }: { title: string; center?: boolean }) {
  return (
    <div className={`flex flex-col gap-[8px] ${center ? 'items-center text-center' : ''}`}>
      <div className="flex items-center gap-[8px]">
        <img src={icMascot} alt="" className="size-[36px]" />
        <img src={icSparkleMain} alt="" className="size-[24px]" />
      </div>
      <h1 className="bg-[linear-gradient(94deg,var(--color-brand-main)_-4.11%,var(--color-brand-main-40)_36.8%,var(--color-brand-main-60)_54.09%)] bg-clip-text text-heading-h1 whitespace-pre-line text-transparent">
        {title}
      </h1>
    </div>
  )
}
