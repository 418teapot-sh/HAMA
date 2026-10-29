import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import Toast from '../components/Toast'
import AccountHelpScreen, { BackToLoginLink, HelpNote } from '../features/auth/AccountHelpScreen'
import { confirmPasswordReset } from '../features/auth/api'
import { splitRecoveryError } from '../features/auth/recovery'
import { useAuthStore } from '../features/auth/store'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

/** TODO: 두 칸이 다를 때 문구는 디자인에 없어 임시로 둡니다. 확인 필요 */
const PASSWORD_MISMATCH = '비밀번호가 일치하지 않아요.'

/**
 * 비밀번호 재설정 — 재설정 메일의 링크(/reset-password?token=...)로 들어오는 화면 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 비밀번호 찾기 · 새 비밀번호 설정: 새 비밀번호 · 새 비밀번호 확인 → "비밀번호 변경하기" (POST /api/v1/auth/password-reset/confirm)
 * 2) 비밀번호 찾기 · 변경 완료: "로그인 하기"
 *
 * - 비밀번호 규칙(영문·숫자·특수문자 포함 8~20자)은 회원가입처럼 백엔드가 검사하고, 오류 문구를 새 비밀번호 칸 아래에 보여 줍니다.
 * - 링크가 만료(30분)됐거나 이미 썼으면 400 INVALID_PASSWORD_RESET_TOKEN → 토스트에 백엔드 문구
 *   (주소에 token 이 없을 때도 백엔드가 같은 식으로 거절합니다)
 * - 바꾸면 모든 기기의 로그인이 풀리므로, 이 브라우저에 남은 로그인 정보도 지우고 로그인 화면으로 보냅니다.
 */
export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const token = useSearchParams()[0].get('token') ?? ''
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [passwordError, setPasswordError] = useState<string>()
  const [confirmError, setConfirmError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [changed, setChanged] = useState(false)

  const canSubmit = password.length > 0 && confirm.length > 0 && !submitting

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!canSubmit) return
    if (password !== confirm) {
      setConfirmError(PASSWORD_MISMATCH)
      return
    }
    setSubmitting(true)
    try {
      await confirmPasswordReset(token, password)
      useAuthStore.getState().clear()
      setChanged(true)
    } catch (error) {
      const { fields, toast: message } = splitRecoveryError(error)
      setPasswordError(fields.newPassword)
      // token 칸은 화면에 없어서 그 오류도 토스트로 보여 줍니다.
      const toastMessage = message ?? fields.token
      if (toastMessage) toast.show(toastMessage)
    } finally {
      setSubmitting(false)
    }
  }

  if (changed) {
    return (
      <AccountHelpScreen title="비밀번호가 변경되었어요" description={'새 비밀번호로 로그인하고\n오늘의 목표를 이어가세요.'}>
        <Button onClick={() => navigate(paths.login, { replace: true })}>로그인 하기</Button>
      </AccountHelpScreen>
    )
  }

  return (
    <>
      <AccountHelpScreen title="새 비밀번호를 설정해주세요" description={'안전한 계정 이용을 위해\n새로운 비밀번호를 입력해주세요.'}>
        <form noValidate onSubmit={handleSubmit} className="flex w-full flex-col gap-[16px]">
          <InputField
            id="reset-password"
            label="새 비밀번호"
            type="password"
            padded={false}
            autoComplete="new-password"
            placeholder="새 비밀번호를 입력해주세요"
            value={password}
            error={passwordError}
            onChange={(e) => {
              setPassword(e.target.value)
              setPasswordError(undefined)
            }}
          />
          <InputField
            id="reset-password-confirm"
            label="새 비밀번호 확인"
            type="password"
            padded={false}
            autoComplete="new-password"
            placeholder="새 비밀번호를 다시 입력해주세요"
            value={confirm}
            error={confirmError}
            onChange={(e) => {
              setConfirm(e.target.value)
              setConfirmError(undefined)
            }}
          />
          <HelpNote>{'영문, 숫자, 특수문자를 포함해\n8–20자로 입력해주세요.'}</HelpNote>
          <Button type="submit" disabled={!canSubmit}>
            비밀번호 변경하기
          </Button>
          <BackToLoginLink />
        </form>
      </AccountHelpScreen>
      <Toast message={toast.message} />
    </>
  )
}
