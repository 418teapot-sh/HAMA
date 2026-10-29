import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import Toast from '../components/Toast'
import AccountHelpScreen, { BackToLoginLink, HelpNote } from '../features/auth/AccountHelpScreen'
import { requestPasswordReset } from '../features/auth/api'
import { splitRecoveryError } from '../features/auth/recovery'
import { EMAIL_FORMAT_ERROR, isValidEmail } from '../features/auth/validation'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

/**
 * 비밀번호 찾기 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 비밀번호 찾기 · 이메일 입력: 이메일 입력 → "재설정 링크 보내기" (POST /api/v1/auth/password-reset)
 * 2) 비밀번호 찾기 · 메일 발송 안내: "메일 다시 보내기" (같은 API 를 다시 부름, 옛 링크는 무효가 됨)
 *    - 메일 확인 안내: 위아래 패딩 20 / 간격 8, Body/B5 (16/600/130%/-0.5%) Brand/Main
 * 메일의 링크(/reset-password?token=...)로 들어오는 화면은 ResetPasswordPage 입니다.
 *
 * 가입 여부를 숨기려고 백엔드는 없는 이메일이어도 성공으로 답합니다. 그래서 안내 문구도 "가입된 계정이 있다면" 입니다.
 * TODO: "메일 다시 보내기" 성공 뒤 안내(토스트 등)는 디자인에 없어 지금은 아무것도 띄우지 않습니다. 확인 필요
 */
export default function FindPasswordPage() {
  const toast = useToast()
  const [email, setEmail] = useState('')
  const [emailError, setEmailError] = useState<string>()
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)

  async function send() {
    setSending(true)
    try {
      await requestPasswordReset(email.trim())
      setSent(true)
    } catch (error) {
      const { fields, toast: message } = splitRecoveryError(error)
      setEmailError(fields.email)
      if (message) toast.show(message)
    } finally {
      setSending(false)
    }
  }

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (sending || email.trim().length === 0) return
    if (!isValidEmail(email)) {
      setEmailError(EMAIL_FORMAT_ERROR)
      return
    }
    void send()
  }

  return (
    <>
      {sent ? (
        <AccountHelpScreen
          title="이메일을 확인해주세요"
          description={'입력한 주소로 가입된 계정이 있다면\n비밀번호 재설정 메일을 보내드렸어요.'}
        >
          <div className="flex w-full flex-col gap-[8px] py-[20px]">
            <p className="text-body-b5 whitespace-pre-line text-brand-main">{'받은 메일의 링크를 눌러\n새 비밀번호를 설정해주세요.'}</p>
          </div>
          <Button disabled={sending} onClick={() => void send()}>
            메일 다시 보내기
          </Button>
          <HelpNote>{'메일이 오지 않았다면 스팸함을 확인하거나\n입력한 이메일 주소를 다시 확인해주세요.'}</HelpNote>
          <BackToLoginLink />
        </AccountHelpScreen>
      ) : (
        <AccountHelpScreen title="비밀번호를 잊으셨나요?" description={'가입한 이메일 주소를 입력해주세요.\n비밀번호 재설정 링크를 보내드릴게요.'}>
          <form noValidate onSubmit={handleSubmit} className="flex w-full flex-col gap-[16px]">
            <InputField
              id="find-password-email"
              label="이메일"
              type="email"
              padded={false}
              autoComplete="email"
              placeholder="이메일을 입력해주세요."
              value={email}
              error={emailError}
              onChange={(e) => {
                setEmail(e.target.value)
                setEmailError(undefined)
              }}
            />
            <Button type="submit" disabled={sending || email.trim().length === 0}>
              재설정 링크 보내기
            </Button>
            <p className="text-body-b8 text-black-gray60">
              이메일이 기억나지 않으세요?{' '}
              <Link to={paths.findEmail}>이메일 찾기</Link>
            </p>
            <BackToLoginLink />
          </form>
        </AccountHelpScreen>
      )}
      <Toast message={toast.message} />
    </>
  )
}
