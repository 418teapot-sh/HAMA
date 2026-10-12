import { useEffect, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { getApiError } from '../api/client'
import icMascot from '../assets/icons/ic_mascot.svg'
import icRight from '../assets/icons/ic_right.svg'
import InputField from '../components/InputField'
import Modal from '../components/Modal'
import NavigationBar from '../components/NavigationBar'
import Toast from '../components/Toast'
import { loadMe, logout, withdraw } from '../features/auth/api'
import { useAuthStore } from '../features/auth/store'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

/** 카드: 반경 20 / 배경 Card_Back / 테두리 1 Stroke / 그림자 shadow */
function InfoCard({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <section
      className={`flex flex-col rounded-[20px] border border-stroke bg-card-back shadow-shadow ${className}`}
    >
      {children}
    </section>
  )
}

/** 약관 목록 한 줄: 높이 40 / Body/B8 Black/Gray80 + 오른쪽 화살표 */
function PolicyRow({ to, children }: { to: string; children: ReactNode }) {
  return (
    <Link to={to} className="flex h-[40px] items-center justify-between px-[20px] text-body-b8 text-black-gray80">
      {children}
      <img src={icRight} alt="" className="size-[20px]" />
    </Link>
  )
}

/** 탈퇴 실패 코드별 안내 */
function withdrawErrorMessage(code: string | undefined) {
  if (code === 'PASSWORD_MISMATCH') return '비밀번호가 맞지 않아요.'
  if (code === 'TOO_MANY_PASSWORD_ATTEMPTS') return '시도 횟수를 넘었어요. 15분 뒤 다시 시도해주세요.'
  return '탈퇴하지 못했어요. 잠시 후 다시 시도해주세요.'
}

/**
 * 마이페이지 (Figma: 마이페이지)
 * - 이름: 마스코트 36 + Heading/H1 Brand/Main
 * - 남은 크레딧 카드: 362×114 / 패딩 20·28 / 제목 Heading/H2 Brand/Main_80, 횟수 Heading/H4 할일(빨강)
 *   카드 아래 오른쪽 "결제하기 >" Body/B8 Black/Gray80 + 화살표 Black/Gray40
 * - 이메일 카드: 362×80 / 제목 Heading/H6 Brand/Main_80, 이메일 Body/B5 Black/Gray60
 * - 약관 목록: 362×80 (줄 40 × 2) / 배경 Card_Back / 테두리 Black/Gray10 / 그림자 없음
 * - 로그아웃: 높이 48 / 반경 12 / 배경 White/W1 / 테두리 Black/Gray20 / Body/B3 Black/Gray60
 *   그 아래 "회원탈퇴" Body/B8 Black/Gray40
 * 회원탈퇴 확인 화면은 디자인에 없어 공용 Modal 에 비밀번호 입력칸을 넣었습니다 (API 가 비밀번호 확인을 요구).
 */
export default function MyPage() {
  const navigate = useNavigate()
  const user = useAuthStore((state) => state.user)
  const toast = useToast()
  const [withdrawOpen, setWithdrawOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [withdrawError, setWithdrawError] = useState<string>()
  const [withdrawing, setWithdrawing] = useState(false)

  // 결제 후 돌아왔을 때 등 남은 크레딧이 바뀌었을 수 있어 들어올 때마다 다시 불러옵니다
  useEffect(() => {
    loadMe()
  }, [])

  const handleLogout = async () => {
    await logout().catch(() => {})
    navigate(paths.login, { replace: true })
  }

  const closeWithdraw = () => {
    setWithdrawOpen(false)
    setPassword('')
    setWithdrawError(undefined)
  }

  const handleWithdraw = async () => {
    if (!password || withdrawing) return
    setWithdrawing(true)
    try {
      await withdraw(password)
      navigate(paths.landing, { replace: true })
    } catch (error) {
      setWithdrawError(withdrawErrorMessage(getApiError(error)?.code))
    } finally {
      setWithdrawing(false)
    }
  }

  return (
    <>
      <NavigationBar title="마이페이지" />

      <main className="flex flex-1 flex-col gap-[20px] px-[20px] pt-[20px] pb-[20px]">
        <p className="flex items-center gap-[8px]">
          <img src={icMascot} alt="" className="size-[36px]" />
          <span className="text-heading-h1 text-brand-main">{user?.name ?? ''}</span>
        </p>

        <div className="flex flex-col gap-[8px]">
          <InfoCard className="gap-[8px] px-[28px] py-[20px]">
            <h2 className="text-heading-h2 text-brand-main-80">남은 크레딧</h2>
            <p className="text-heading-h4 text-category-task">{user ? `${user.credits}회` : '-'}</p>
          </InfoCard>
          {/* TODO: 결제 화면·API 가 정해지면 연결합니다 */}
          <button
            type="button"
            onClick={() => toast.show('결제는 곧 열릴 예정이에요.')}
            className="flex items-center gap-[2px] self-end text-body-b8 text-black-gray80"
          >
            결제하기
            <img src={icRight} alt="" className="size-[20px]" />
          </button>
        </div>

        <InfoCard className="gap-[4px] px-[20px] py-[16px]">
          <h2 className="text-heading-h6 font-semibold text-brand-main-80">이메일</h2>
          <p className="text-body-b5 font-normal text-black-gray60">{user?.email ?? ''}</p>
        </InfoCard>

        <nav className="flex flex-col divide-y divide-black-gray10 rounded-[20px] border border-black-gray10 bg-card-back">
          <PolicyRow to={paths.terms}>서비스 이용 약관</PolicyRow>
          <PolicyRow to={paths.privacy}>개인정보 수집 및 이용</PolicyRow>
        </nav>

        <div className="mt-auto flex flex-col items-center gap-[12px]">
          <button
            type="button"
            onClick={handleLogout}
            className="h-[48px] w-full rounded-[12px] border border-black-gray20 bg-white-w1 text-body-b3 text-black-gray60"
          >
            로그아웃
          </button>
          <button type="button" onClick={() => setWithdrawOpen(true)} className="text-body-b8 text-black-gray40">
            회원탈퇴
          </button>
        </div>
      </main>

      <Modal
        open={withdrawOpen}
        title="정말 탈퇴할까요?"
        description="투두·일정·목표 등 모든 기록이 지워지고 되돌릴 수 없어요."
        cancelText="닫기"
        confirmText={withdrawing ? '탈퇴하는 중…' : '탈퇴하기'}
        onCancel={closeWithdraw}
        onConfirm={handleWithdraw}
      >
        <InputField
          id="withdraw-password"
          label="비밀번호 확인"
          type="password"
          autoComplete="current-password"
          placeholder="비밀번호를 입력해주세요."
          value={password}
          onChange={(e) => {
            setPassword(e.target.value)
            setWithdrawError(undefined)
          }}
          error={withdrawError}
          className="px-0!"
        />
      </Modal>

      <Toast message={toast.message} />
    </>
  )
}
