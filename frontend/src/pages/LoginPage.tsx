import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import NavigationBar from '../components/NavigationBar'
import Toast from '../components/Toast'
import { useToast } from '../hooks/useToast'
import { login } from '../features/auth/api'
import { paths } from '../paths'

/** Figma 로그인 오류 화면의 토스트 문구 그대로 */
const LOGIN_FAILED_MESSAGE = '로그인에 실패했어요. 다시 시도해 주세요'

/**
 * 로그인 화면 (Figma > UI > 로그인 > 로그인 화면 / 로그인 오류)
 *
 * 구조 (Figma 레이어 기준)
 * - NavigationBar: 왼쪽 닫기(ic_close)
 * - Container: 높이 170 / 위 30·아래 20 패딩 / 간격 10 → 로고 자리
 * - 폼 프레임: 세로 간격 12
 *   - InputField 이메일, InputField 비밀번호
 *   - 하단 프레임: 좌우 패딩 20 / 세로 간격 20
 *     - 이메일 찾기 · 비밀번호 찾기 (space-between, Body/B6 Gray40)
 *     - 로그인 버튼
 *     - 계정이 없으신가요?(Gray40) + 회원가입(Gray80, 아래 테두리 1px Gray80·아래 패딩 2) / 간격 12
 */
export default function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const toast = useToast()

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (submitting) return
    setSubmitting(true)
    try {
      await login({ email, password })
      navigate(paths.home, { replace: true })
    } catch {
      // TODO: 401(정보 불일치)·429(시도 횟수 초과)·400(입력 누락)을 모두 같은 문구로 보여줄지 확인 필요
      toast.show(LOGIN_FAILED_MESSAGE)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <NavigationBar
        leftLabel="닫기"
        // TODO: ic_close 아이콘 SVG 를 Figma 에서 내보내 받으면 넣습니다(24×24, 벡터 14×14, Black/Black).
        leftIcon={<span className="size-[24px]" />}
        // TODO: 닫기 버튼을 눌렀을 때 이동할 화면 확인 필요(랜딩 페이지 예정)
        onLeftClick={undefined}
      />

      <main className="flex w-full flex-col">
        <div className="flex h-[170px] w-full flex-col items-center justify-center gap-[10px] pt-[30px] pb-[20px]">
          {/* TODO: 로고 이미지가 나오면 교체 (Figma 에는 '로고 자리' 텍스트만 있음) */}
          <span className="text-caption-cap1 text-brand-main">로고 자리</span>
        </div>

        <form noValidate onSubmit={handleSubmit} className="flex w-full flex-col gap-[12px]">
          <InputField
            id="login-email"
            label="이메일"
            type="email"
            autoComplete="email"
            placeholder="이메일을 입력해주세요."
            value={email}
            onChange={(e) => setEmail(e.target.value)}
          />
          <InputField
            id="login-password"
            label="비밀번호"
            type="password"
            autoComplete="current-password"
            placeholder="비밀번호를 입력해주세요."
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />

          <div className="flex w-full flex-col gap-[20px] px-[20px]">
            <div className="flex w-full items-center justify-between">
              {/* TODO: 이메일 찾기·비밀번호 찾기는 백엔드 API 가 없어 동작 미정 */}
              <button type="button" className="text-body-b6 text-black-gray40">
                이메일 찾기
              </button>
              <button type="button" className="text-body-b6 text-black-gray40">
                비밀번호 찾기
              </button>
            </div>

            <Button type="submit" disabled={submitting}>
              로그인
            </Button>

            <div className="flex w-full items-center justify-center gap-[12px]">
              <span className="text-body-b6 text-black-gray40">계정이 없으신가요?</span>
              <Link
                to={paths.signup}
                className="flex items-center gap-[10px] border-b border-black-gray80 pb-[2px] text-body-b6 text-black-gray80"
              >
                회원가입
              </Link>
            </div>
          </div>
        </form>
      </main>

      <Toast message={toast.message} />
    </>
  )
}
