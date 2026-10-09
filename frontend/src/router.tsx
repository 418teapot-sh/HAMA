import { createBrowserRouter } from 'react-router'
import MobileLayout from './layouts/MobileLayout'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import SignupPage from './pages/SignupPage'
import { paths } from './paths'

export const router = createBrowserRouter([
  {
    element: <MobileLayout />,
    children: [
      { path: paths.home, element: <HomePage /> },
      { path: paths.login, element: <LoginPage /> },
      { path: paths.signup, element: <SignupPage /> },
    ],
  },
])
