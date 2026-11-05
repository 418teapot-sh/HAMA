import { isRouteErrorResponse, useNavigate, useRouteError } from 'react-router'
import Button from '../components/Button'
import GoalHeader from '../features/goal/components/GoalHeader'
import { paths } from '../paths'

/**
 * 없는 주소 · 화면 오류 (디자인 없음, 목표 추가 완료 화면과 같은 머리말 + 공용 Button)
 * - 없는 주소: router 의 '*' 경로가 그리거나, 라우터가 404 를 던졌을 때
 * - 그 밖의 오류: 화면을 그리다 예외가 나면 라우터 기본 화면("Unexpected Application Error!") 대신 이 화면
 * 화면 패딩 20 / 머리말 ↔ 안내 간격 12 / 버튼은 아래 고정 패딩 20
 */
export default function ErrorPage() {
  const navigate = useNavigate()
  const error = useRouteError()
  // '*' 경로로 들어오면 error 가 없음 → 없는 주소
  const notFound = !error || (isRouteErrorResponse(error) && error.status === 404)

  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-[402px] flex-col bg-white-w1">
      <main className="flex flex-1 flex-col items-center justify-center gap-[12px] px-[20px]">
        <GoalHeader title={notFound ? '페이지를\n찾을 수 없어요.' : '문제가\n생겼어요.'} center />
        <p className="text-center text-body-b8 whitespace-pre-line text-black-gray60">
          {notFound ? '주소가 바뀌었거나 없는 페이지예요.' : '잠시 후 다시 시도해주세요.\n계속되면 앱을 새로고침해주세요.'}
        </p>
      </main>
      <div className="p-[20px]">
        <Button onClick={() => navigate(paths.home, { replace: true })}>홈으로 가기</Button>
      </div>
    </div>
  )
}
