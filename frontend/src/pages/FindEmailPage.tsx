import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router'
import Button from '../components/Button'
import InputField from '../components/InputField'
import AccountHelpScreen, { HelpNote } from '../features/auth/AccountHelpScreen'
import { paths } from '../paths'

/**
 * 이메일 찾기 (Figma UI > 이메일, 비밀번호 찾기)
 * 1) 이메일 찾기 · 계정 확인 요청: 이름 · 답변 받을 이메일 입력 → "계정 확인 요청하기"
 * 2) 이메일 찾기 · 요청 접수 완료: "로그인으로 돌아가기" 버튼
 *
 * 페이지 구현 단계: 백엔드 API 가 아직 없어 요청을 보내지 않고 바로 2) 로 넘어갑니다.
 * TODO: 요청 API 가 생기면 연동 · 입력 검사(빈 값, 이메일 형식)와 오류 표시 방식 확인 필요 (디자인에 없음)
 */
export default function FindEmailPage() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [requested, setRequested] = useState(false)

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setRequested(true)
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
    <AccountHelpScreen title="이메일이 기억나지 않으세요?" description={'가입할 때 사용한 이름과\n답변받을 이메일을 알려주세요.'}>
      <form noValidate onSubmit={handleSubmit} className="flex w-full flex-col gap-[16px]">
        <InputField
          id="find-email-name"
          label="이름"
          padded={false}
          autoComplete="name"
          placeholder="이름을 입력해주세요"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <InputField
          id="find-email-contact"
          label="답변 받을 이메일"
          type="email"
          padded={false}
          autoComplete="email"
          placeholder="확인 가능한 이메일을 입력해주세요"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Button type="submit">계정 확인 요청하기</Button>
        <HelpNote>{'고객지원에서 가입 정보를 확인한 후 안내드려요.\n확인을 위해 추가 정보가 필요할 수 있어요.'}</HelpNote>
      </form>
    </AccountHelpScreen>
  )
}
