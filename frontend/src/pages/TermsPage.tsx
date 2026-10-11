import PolicyPage from '../features/terms/PolicyPage'
import { TERMS } from '../features/terms/policies'

/** 이용약관 (/terms) · 문구와 틀은 features/terms 에 있습니다. */
export default function TermsPage() {
  return <PolicyPage doc={TERMS} />
}
