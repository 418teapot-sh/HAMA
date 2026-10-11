import PolicyPage from '../features/terms/PolicyPage'
import { PRIVACY } from '../features/terms/policies'

/** 개인정보처리방침 (/privacy) · 문구와 틀은 features/terms 에 있습니다. */
export default function PrivacyPage() {
  return <PolicyPage doc={PRIVACY} />
}
