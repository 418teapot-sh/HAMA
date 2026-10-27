import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import AccountHelpScreen, { BackToLoginLink, HelpNote } from '../features/auth/AccountHelpScreen'
import { paths } from '../paths'

/**
 * 비밀번호 찾기 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 비밀번호 찾기 · 이메일 입력: 이메일 입력 → "재설정 링크 보내기"
 * 2) 비밀번호 찾기 · 메일 발송 안내: "메일 다시 보내기"
 *    - 메일 확인 안내: 위아래 패딩 20 / 간격 8, Body/B5 (16/600/130%/-0.5%) Brand/Main
 * 메일의 링크를 누른 뒤 화면(새 비밀번호 설정 · 변경 완료)은 ResetPasswordPage 입니다.
 *
 * 페이지 구현 단계: 백엔드 API 가 아직 없어 메일을 보내지 않고 바로 2) 로 넘어갑니다.
 * TODO: 재설정 메일 API 가 생기면 연동 · "메일 다시 보내기"를 눌렀을 때의 안내(토스트 등)는 디자인에 없어 확인 필요
 */
export default function FindPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setSent(true)
  }

  if (sent) {
    return (
      <AccountHelpScreen
        title="이메일을 확인해주세요"
        description={'입력한 주소로 가입된 계정이 있다면\n비밀번호 재설정 메일을 보내드렸어요.'}
      >
        <div className="flex w-full flex-col gap-[8px] py-[20px]">
          <p className="text-body-b5 whitespace-pre-line text-brand-main">{'받은 메일의 링크를 눌러\n새 비밀번호를 설정해주세요.'}</p>
        </div>
        <Button>메일 다시 보내기</Button>
        <HelpNote>{'메일이 오지 않았다면 스팸함을 확인하거나\n입력한 이메일 주소를 다시 확인해주세요.'}</HelpNote>
        <BackToLoginLink />
      </AccountHelpScreen>
    )
  }

  return (
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
          onChange={(e) => setEmail(e.target.value)}
        />
        <Button type="submit">재설정 링크 보내기</Button>
        <p className="text-body-b8 text-black-gray60">
          이메일이 기억나지 않으세요?{' '}
          <Link to={paths.findEmail}>이메일 찾기</Link>
        </p>
        <BackToLoginLink />
      </form>
    </AccountHelpScreen>
  )
}
