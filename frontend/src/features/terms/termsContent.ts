/**
 * 이용약관 문구 (Figma 온보딩 > 이용약관 · Mobile · 390 / 이용약관 · Desktop · 1512 그대로)
 * 대괄호 [ ] 안의 문구는 Figma 에 자리 표시로 들어가 있는 것이라 그대로 둡니다. (실제 문구는 정해지면 교체)
 * 자간은 Figma 조항마다 달라 그대로 적었습니다. (모바일 · 데스크톱이 다름)
 */
export interface TermsArticleContent {
  /** 목차에 보이는 이름 (앞 번호는 목차에서 붙임) */
  tocLabel: string
  title: string
  /** 줄바꿈 \n 은 Figma 에서 문단을 나눈 자리 */
  body: string
  /** 본문 자간 · 모바일 */
  tracking?: string
  /** 본문 자간 · 데스크톱 */
  desktopTracking?: string
}

export const TERMS_UPDATED_AT = '최종 수정일 [YYYY. MM. DD.] · 시행일 [YYYY. MM. DD.]'

export const TERMS_ARTICLES: TermsArticleContent[] = [
  {
    tocLabel: '서비스 제공자',
    title: '제1조 서비스 제공자',
    body: '본 서비스 HAMA는 [법적 이름](이하 "운영자")이 제공하며, 이용자는 운영자와 직접 계약을 체결합니다.',
  },
  {
    tocLabel: '약관의 동의',
    title: '제2조 약관의 동의',
    body: '[본문 작성 — 서비스를 계속 이용하면 본 약관에 동의한 것으로 본다는 취지]',
  },
  {
    tocLabel: '금지되는 행위',
    title: '제3조 금지되는 행위',
    body: '[본문 작성 — 최소한 네 가지: 불법적 이용 / 사기·스팸 / 타인의 지식재산권 침해 / 보안 침해(멀웨어, 취약점 탐색, 무단 스크레이핑)]',
    tracking: '-0.03em',
    desktopTracking: '-0.03em',
  },
  {
    tocLabel: '지식재산권',
    title: '제4조 지식재산권',
    body: '[본문 작성 — 서비스와 그 지식재산권은 운영자에게 귀속, 이용자가 입력한 목표·할 일 데이터의 소유권은 이용자에게 있다는 점도 함께]',
    tracking: '-0.03em',
    desktopTracking: '-0.03em',
  },
  {
    tocLabel: '서비스 수준',
    title: '제5조 서비스 수준',
    body: '[본문 작성 — 무중단·무오류를 보장하지 않으며 점검 등으로 중단될 수 있다는 취지]',
    tracking: '-0.03em',
  },
  {
    tocLabel: '크레딧 및 결제 조건',
    title: '제6조 크레딧 및 결제 조건',
    body: '[본문 작성 — 크레딧 1회의 정의와 차감 시점 / 묶음 단위와 금액 / 크레딧 유효기간 / 결제 수단과 세금 / 일회성 결제이며 자동 갱신이 없다는 점 / 미사용 크레딧의 처리]',
    tracking: '-0.03em',
    desktopTracking: '-0.02em',
  },
  {
    tocLabel: '이용 정지 및 해지',
    title: '제7조 이용 정지 및 해지',
    body: '[본문 작성 — 중대한 위반, 미결제, 보안·사기 리스크가 있을 때의 정지·해지 사유와 절차]',
    tracking: '-0.03em',
  },
  {
    tocLabel: 'AI 생성 기능',
    title: '제8조 AI 생성 기능',
    body: '[본문 작성 — 허용·금지 사용, 입력물에 대한 이용자 책임, 생성물의 권리와 신고 경로, 콘텐츠 검수 권한]\n\nHAMA가 생성하는 일정과 제안은 참고용이며 정확성을 보장하지 않습니다. 의료·법률·재무 등 전문 영역의 판단에 사용하도록 설계되지 않았습니다.',
    tracking: '-0.03em',
  },
  {
    tocLabel: '결제 처리 주체',
    title: '제9조 결제 처리 주체',
    body: '주문 처리는 온라인 리셀러인 Paddle.com이 담당하며, 모든 주문에 대해 Paddle.com이 Merchant of Record입니다.',
    tracking: '-0.03em',
  },
  {
    tocLabel: '문의처',
    title: '제10조 문의처',
    body: '[support@도메인] · 운영자 [법적 이름] · 소재지 [주소]',
  },
]

/** 제9조 Paddle 고지 (영문 원문, Paddle 이 요구하는 문구) */
export const PADDLE_NOTICE =
  'Our order process is conducted by our online reseller Paddle.com. Paddle.com is the Merchant of Record for all our orders.'

/** 조항으로 스크롤할 때 쓰는 id */
export const articleId = (index: number) => `terms-article-${index + 1}`
