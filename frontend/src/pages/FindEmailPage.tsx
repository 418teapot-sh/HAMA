import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import Toast from '../components/Toast'
import AccountHelpScreen, { HelpNote } from '../features/auth/AccountHelpScreen'
import { submitEmailInquiry } from '../features/auth/api'
import { splitRecoveryError } from '../features/auth/recovery'
import { EMAIL_FORMAT_ERROR, isValidEmail } from '../features/auth/validation'
import { useToast } from '../hooks/useToast'
import { paths } from '../paths'

/**
 * 이메일 찾기 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 이메일 찾기 · 계정 확인 요청: 이름 · 답변 받을 이메일 입력 → "계정 확인 요청하기" (POST /api/v1/auth/email-inquiry)
 * 2) 이메일 찾기 · 요청 접수 완료: "로그인으로 돌아가기" 버튼
 *
 * 운영자가 가입 정보를 확인해 답변 이메일로 안내합니다(자동으로 찾아주지 않음).
 * 버튼은 두 칸을 다 채워야 켜집니다(회원가입과 같은 방식). 이메일 형식 오류 문구는 로그인 화면과 같습니다.
 */
export default function FindEmailPage() {
  const navigate = useNavigate()
  const toast = useToast()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [requested, setRequested] = useState(false)

  /** 입력을 고치면 그 칸의 에러 문구만 지웁니다. */
  function clearError(field: string) {
    setErrors((prev) => {
      const next = { ...prev }
      delete next[field]
      return next
    })
  }

  const canSubmit = name.trim().length > 0 && email.trim().length > 0 && !submitting

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!canSubmit) return
    if (!isValidEmail(email)) {
      setErrors({ email: EMAIL_FORMAT_ERROR })
      return
    }
    setSubmitting(true)
    try {
      await submitEmailInquiry(name.trim(), email.trim())
      setRequested(true)
    } catch (error) {
      const { fields, toast: message } = splitRecoveryError(error)
      setErrors(fields)
      if (message) toast.show(message)
    } finally {
      setSubmitting(false)
    }
  }

  if (requested) {
    return (
      <AccountHelpScreen title="계정 확인 요청을 받았어요" description={'남겨주신 이메일로 안내드릴게요.\n답변 메일의 내용을 확인해주세요.'}>
        <Button onClick={() => navigate(paths.login)}>로그인으로 돌아가기</Button>
        <HelpNote>답변이 보이지 않으면 스팸함도 확인해주세요.</HelpNote>
      </AccountHelpScreen>
    )
  }

  return (
    <>
      <AccountHelpScreen title="이메일이 기억나지 않으세요?" description={'가입할 때 사용한 이름과\n답변받을 이메일을 알려주세요.'}>
        <form noValidate onSubmit={handleSubmit} className="flex w-full flex-col gap-[16px]">
          <InputField
            id="find-email-name"
            label="이름"
            padded={false}
            autoComplete="name"
            maxLength={50}
            placeholder="이름을 입력해주세요"
            value={name}
            error={errors.name}
            onChange={(e) => {
              setName(e.target.value)
              clearError('name')
            }}
          />
          <InputField
            id="find-email-contact"
            label="답변 받을 이메일"
            type="email"
            padded={false}
            autoComplete="email"
            placeholder="확인 가능한 이메일을 입력해주세요"
            value={email}
            error={errors.email}
            onChange={(e) => {
              setEmail(e.target.value)
              clearError('email')
            }}
          />
          <Button type="submit" disabled={!canSubmit}>
            계정 확인 요청하기
          </Button>
          <HelpNote>{'고객지원에서 가입 정보를 확인한 후 안내드려요.\n확인을 위해 추가 정보가 필요할 수 있어요.'}</HelpNote>
        </form>
      </AccountHelpScreen>
      <Toast message={toast.message} />
    </>
  )
}
