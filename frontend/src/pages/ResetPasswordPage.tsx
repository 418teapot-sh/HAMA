import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import AccountHelpScreen, { BackToLoginLink, HelpNote } from '../features/auth/AccountHelpScreen'
import { paths } from '../paths'

/**
 * 비밀번호 재설정 — 재설정 메일의 링크로 들어오는 화면 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 비밀번호 찾기 · 새 비밀번호 설정: 새 비밀번호 · 새 비밀번호 확인 → "비밀번호 변경하기"
 * 2) 비밀번호 찾기 · 변경 완료: "로그인 하기"
 *
 * 페이지 구현 단계: 백엔드 API 가 아직 없어 바로 2) 로 넘어갑니다.
 * TODO: 재설정 API 가 생기면 링크의 토큰 받는 방식 · 연동, 비밀번호 규칙 · 불일치 오류 표시 방식 확인 필요 (디자인에 없음)
 */
export default function ResetPasswordPage() {
  const navigate = useNavigate()
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [changed, setChanged] = useState(false)

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setChanged(true)
  }

  if (changed) {
    return (
      <AccountHelpScreen title="비밀번호가 변경되었어요" description={'새 비밀번호로 로그인하고\n오늘의 목표를 이어가세요.'}>
        <Button onClick={() => navigate(paths.login, { replace: true })}>로그인 하기</Button>
      </AccountHelpScreen>
    )
  }

  return (
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
          onChange={(e) => setPassword(e.target.value)}
        />
        <InputField
          id="reset-password-confirm"
          label="새 비밀번호 확인"
          type="password"
          padded={false}
          autoComplete="new-password"
          placeholder="새 비밀번호를 다시 입력해주세요"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
        />
        <HelpNote>{'영문, 숫자, 특수문자를 포함해\n8–20자로 입력해주세요.'}</HelpNote>
        <Button type="submit">비밀번호 변경하기</Button>
        <BackToLoginLink />
      </form>
    </AccountHelpScreen>
  )
}
