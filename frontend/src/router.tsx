import { createBrowserRouter } from 'react-router'
import RequireAuth from './features/auth/RequireAuth'
import MobileLayout from './layouts/MobileLayout'
import TabLayout from './layouts/TabLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import MyPage from './pages/MyPage'
import ReportPage from './pages/ReportPage'
import SignupPage from './pages/SignupPage'
import TodoPage from './pages/TodoPage'
import { paths } from './paths'

export const router = createBrowserRouter([
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
