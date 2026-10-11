import { createBrowserRouter } from 'react-router'
import RequireAuth from './features/auth/RequireAuth'
import MobileLayout from './layouts/MobileLayout'
import TabLayout from './layouts/TabLayout'
import HomePage from './pages/HomePage'
import LandingPage from './pages/LandingPage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import PrivacyPage from './pages/PrivacyPage'
import RefundPage from './pages/RefundPage'
import ReportPage from './pages/ReportPage'
import SignupPage from './pages/SignupPage'
import TermsPage from './pages/TermsPage'
import TodoPage from './pages/TodoPage'
import { paths } from './paths'

export const router = createBrowserRouter([
  // 랜딩 · 약관 · 정책 페이지는 넓은 화면에서 데스크톱 디자인으로 보여 주므로 모바일 폭(402) 레이아웃 밖에 둡니다.
  { path: paths.landing, element: <LandingPage /> },
  { path: paths.terms, element: <TermsPage /> },
  { path: paths.refund, element: <RefundPage /> },
  { path: paths.privacy, element: <PrivacyPage /> },
  {
    element: <MobileLayout />,
    children: [
      {
        element: <RequireAuth />,
        children: [
          {
            element: <TabLayout />,
            children: [
              { path: paths.home, element: <HomePage /> },
              { path: paths.todos, element: <TodoPage /> },
              { path: paths.report, element: <ReportPage /> },
              { path: paths.mypage, element: <MyPage /> },
            ],
          },
        ],
      },
      { path: paths.login, element: <LoginPage /> },
      { path: paths.signup, element: <SignupPage /> },
    ],
  },
])
