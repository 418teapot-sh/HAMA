import type { ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import icClose from '../../assets/icons/ic_close.svg'
import NavigationBar from '../../components/NavigationBar'
import { paths } from '../../paths'

interface AccountHelpScreenProps {
  title: string
  /** 안내 문구. 줄바꿈(\n)은 Figma 줄바꿈 그대로 */
  description: string
  children: ReactNode
}

/**
 * 이메일 · 비밀번호 찾기 화면 공통 틀 (Figma UI > 이메일, 비밀번호 찾기 6개 프레임)
 *
 * - NavigationBar Variant2: 왼쪽 닫기(ic_close), 제목 숨김 → 닫으면 로그인 화면으로
 * - 콘텐츠: 세로 / 패딩 20 / 간격 32
 *   - 화면 안내 (간격 12): 제목 Heading/H2 (24/700/150%) Black/Black · 안내 Body/B6 (16/400/150%) Black/Gray60
 *   - 입력 및 확인 (간격 16): children
 */
export default function AccountHelpScreen({ title, description, children }: AccountHelpScreenProps) {
  const navigate = useNavigate()

  return (
    <>
      <NavigationBar
        variant="close"
        leftLabel="닫기"
        leftIcon={<img src={icClose} alt="" className="size-[24px]" />}
        onLeftClick={() => navigate(paths.login)}
      />
      <main className="flex w-full flex-col gap-[32px] p-[20px]">
        <section className="flex w-full flex-col gap-[12px]">
          <h1 className="text-heading-h2 font-bold text-black-black">{title}</h1>
          <p className="text-body-b6 whitespace-pre-line text-black-gray60">{description}</p>
        </section>
        <div className="flex w-full flex-col gap-[16px]">{children}</div>
      </main>
    </>
  )
}

/** 안내 작은 글씨 (Body/B8 14/400/150% Black/Gray60) */
export function HelpNote({ children }: { children: ReactNode }) {
  return <p className="text-body-b8 whitespace-pre-line text-black-gray60">{children}</p>
}

/** "로그인으로 돌아가기" 글자 링크 (Body/B6 Brand/Main, 가운데) */
export function BackToLoginLink() {
  return (
    <Link to={paths.login} className="w-full text-center text-body-b6 text-brand-main">
      로그인으로 돌아가기
    </Link>
  )
}
