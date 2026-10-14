import { useLocation, useNavigate } from 'react-router'
import IconAlert from '../../components/IconAlert'
import { SparkleIcon } from '../../components/TodoIcons'
import { paths } from '../../paths'

interface CreditShortageModalProps {
  open: boolean
  /** 남은 크레딧 (보통 0) */
  credits: number
  onClose: () => void
}

/**
 * 크레딧 부족 팝업 (Figma 결제 > 크레딧 부족)
 * 크레딧 0회에서 + 버튼(목표 추가) 또는 일정 재계산을 눌렀을 때 공통으로 띄웁니다. (Figma 캡션)
 * - 아이콘 sparkle / "크레딧이 부족해요" / 안내 2줄 / "남은 크레딧 0회" Brand/EO
 * - [나중에 (너비 136)] [충전하기] → 충전 화면. 충전이 끝나면 지금 화면으로 돌아오도록 위치를 넘깁니다.
 */
export default function CreditShortageModal({ open, credits, onClose }: CreditShortageModalProps) {
  const navigate = useNavigate()
  const location = useLocation()

  return (
    <IconAlert
      open={open}
      icon={<SparkleIcon className="size-[24px]" />}
      title="크레딧이 부족해요"
      lines={['목표 생성과 일정 재계산에는', '크레딧 1회가 필요해요.']}
      note={`남은 크레딧 ${credits}회`}
      noteClass="text-brand-eo"
      cancelText="나중에"
      onCancel={onClose}
      cancelClassName="w-[136px] shrink-0"
      confirmText="충전하기"
      onConfirm={() => navigate(paths.charge, { state: { from: location.pathname } })}
    />
  )
}
