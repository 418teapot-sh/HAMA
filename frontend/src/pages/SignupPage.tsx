import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import { getApiError } from '../api/client'
import Agreement, { type AgreementItem } from '../components/Agreement'
import Button from '../components/Button'
import InputField from '../components/InputField'
import NavigationBar from '../components/NavigationBar'
import Toast from '../components/Toast'
import { signup } from '../features/auth/api'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'
import icBack from '../assets/icons/ic_back.svg'
import icEyesNoSee from '../assets/icons/ic_eyes_nosee.svg'
import icEyesSee from '../assets/icons/ic_eyes_see.svg'

/** Figma 약관 동의 항목 문구 그대로 */
const AGREEMENT_ITEMS: AgreementItem[] = [
  { key: 'service', label: '서비스 이용 약관 동의 (필수)', required: true },
  { key: 'privacy', label: '개인정보 수집 및 이용 동의 (필수)', required: true },
  { key: 'age', label: '만 14세 이상 확인 (필수)', required: true },
  { key: 'marketing', label: '마케팅 알림 수신 동의 (선택)', required: false },
]

/**
 * 회원가입 화면 (Figma > UI > 회원가입: 자체 회원가입 / 비밀번호 가리기·보이기 / 회원가입 입력 완료 / 회원가입 완료)
 *
 * 구조 (Figma 레이어 기준)
 * - NavigationBar(default): 뒤로가기(ic_back) + 제목 "회원가입"
 * - 본문: 세로 간격 10 / 위아래 패딩 20 → 이름 · 이메일 · 비밀번호 InputField, Agreement
 * - 하단 버튼 영역: 패딩 20 → 회원가입 버튼 (입력 전: 비활성 Gray20, 입력 완료: Brand/Main)
 * - 가입 성공 시 회원가입 완료 화면으로 바뀜
 */
export default function SignupPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [agreements, setAgreements] = useState<Record<string, boolean>>({})
  const [emailError, setEmailError] = useState<string>()
  const [nameError, setNameError] = useState<string>()
  const [passwordError, setPasswordError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  const requiredAgreed = AGREEMENT_ITEMS.filter((item) => item.required).every((item) => agreements[item.key])
  // TODO: 버튼 활성 조건(필수 입력 + 필수 약관)과 비밀번호 규칙(디자인 8-20자 vs 백엔드 8~64자) 확인 필요
  // 이메일 형식 오류 표시는 디자인에서 로그인 화면으로 옮겨져, 회원가입은 백엔드 검증(VALIDATION_FAILED) 결과만 보여줍니다.
  const canSubmit = name.trim().length > 0 && email.trim().length > 0 && password.length > 0 && requiredAgreed && !submitting

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!canSubmit) return
    setSubmitting(true)
    try {
      await signup({ name: name.trim(), email: email.trim(), password })
      setDone(true)
    } catch (error) {
      const apiError = getApiError(error)
      if (apiError?.code === 'EMAIL_ALREADY_EXISTS') {
        // TODO: 중복 이메일 오류 디자인이 없어 이메일 오류와 같은 자리에 백엔드 문구를 보여줌. 확인 필요
        setEmailError(apiError.message)
      } else if (apiError?.code === 'VALIDATION_FAILED' && apiError.fields) {
        setNameError(apiError.fields.name)
        setEmailError(apiError.fields.email)
        setPasswordError(apiError.fields.password)
      } else {
        // TODO: 그 밖의 실패 안내 디자인이 없어 토스트에 백엔드 문구를 보여줌. 확인 필요
        toast.show(apiError?.message ?? '잠시 후 다시 시도해 주세요')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (done) {
    return (
      <>
        <main className="flex w-full flex-1 flex-col items-center justify-center gap-[10px] p-[10px]">
          <div className="flex flex-col items-center gap-[20px]">
            {/* TODO: 로고 이미지가 나오면 교체 (Figma 에는 '로고 자리' 텍스트만 있음, Heading/H3 400 #000000) */}
            <span className="text-heading-h3 text-black">로고 자리</span>
            <p className="text-heading-h3 font-bold text-brand-main-80">회원가입이 완료되었습니다</p>
          </div>
        </main>
        <div className="w-full p-[20px]">
          {/* TODO: 가입하면 백엔드가 바로 로그인 처리하지만, 디자인대로 로그인 화면으로 보냄. 홈으로 바로 갈지 확인 필요 */}
          <Button onClick={() => navigate(paths.login, { replace: true })}>로그인하러가기</Button>
        </div>
      </>
    )
  }

  return (
    <>
      <NavigationBar
        title="회원가입"
        leftLabel="뒤로 가기"
        leftIcon={<img src={icBack} alt="" className="size-[24px]" />}
        onLeftClick={() => navigate(-1)}
      />

      <form noValidate onSubmit={handleSubmit} className="flex w-full flex-1 flex-col">
        <main className="flex w-full flex-1 flex-col gap-[10px] py-[20px]">
          <InputField
            id="signup-name"
            label="이름"
            autoComplete="name"
            placeholder="이름을 입력해주세요."
            value={name}
            error={nameError}
            onChange={(e) => {
              setName(e.target.value)
              setNameError(undefined)
            }}
          />
          <InputField
            id="signup-email"
            label="이메일"
            type="email"
            autoComplete="email"
            placeholder="예시: example@hama.com"
            value={email}
            error={emailError}
            onChange={(e) => {
              setEmail(e.target.value)
              setEmailError(undefined)
            }}
          />
          <InputField
            id="signup-password"
            label="비밀번호"
            type={showPassword ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="영문, 숫자, 특수문자를 포함하여 8-20자로 작성"
            value={password}
            error={passwordError}
            onChange={(e) => {
              setPassword(e.target.value)
              setPasswordError(undefined)
            }}
            rightSlot={
              <button
                type="button"
                aria-label={showPassword ? '비밀번호 숨기기' : '비밀번호 보기'}
                aria-pressed={showPassword}
                onClick={() => setShowPassword((v) => !v)}
                className="size-[25px] shrink-0"
              >
                {/* Figma eyes: 가린 상태 NoSee, 보이는 상태 See (25×25) */}
                <img src={showPassword ? icEyesSee : icEyesNoSee} alt="" className="size-[25px]" />
              </button>
            }
          />
          <Agreement items={AGREEMENT_ITEMS} checked={agreements} onChange={setAgreements} />
        </main>

        <div className="w-full p-[20px]">
          <Button type="submit" disabled={!canSubmit}>
            회원가입
          </Button>
        </div>
      </form>

      <Toast message={toast.message} />
    </>
  )
}
