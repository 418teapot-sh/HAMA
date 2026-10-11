import PolicyPage from '../features/terms/PolicyPage'
import { REFUND } from '../features/terms/policies'

/** 환불 및 청약철회 정책 (/refund) · 문구와 틀은 features/terms 에 있습니다. */
export default function RefundPage() {
  return <PolicyPage doc={REFUND} />
}
