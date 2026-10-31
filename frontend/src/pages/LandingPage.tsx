import { useEffect } from 'react'
import { useLocation } from 'react-router'
import hippo from '../assets/landing/hippo.webp'
import page from '../assets/landing/page.webp'
import previewAiDone from '../assets/landing/preview-ai-done.webp'
import previewAiInput from '../assets/landing/preview-ai-input.webp'
import previewTodo from '../assets/landing/preview-todo.webp'
import share from '../assets/landing/share.webp'
import target from '../assets/landing/target.webp'
import InfoCard from '../features/landing/InfoCard'
import LandingSection, { SectionHeader } from '../features/landing/LandingSection'
import Lines from '../features/landing/Lines'
import PlanCard from '../features/landing/PlanCard'
import SignupCta from '../features/landing/SignupCta'
import { useDesktopZoom } from '../features/landing/useDesktopZoom'
import { OPERATOR_EMAIL, OPERATOR_INFO, POLICY_DOCS } from '../features/terms/policies'
import PolicyLink from '../features/terms/PolicyLink'

/** 02 · 서비스 이용 흐름 카드 (줄바꿈은 Figma 그대로) */
const FLOW = [
  { image: target, title: '목표 작성하기', description: ['문장으로 AI와 함께할', '목표 작성하기'] },
  { image: page, title: 'AI 투두리스트 생성', description: ['작성한 목표를 바탕으로', 'AI가 투두리스트 생성'] },
  { image: share, title: '리포트로 관리', description: ['진행한 투두리스트 기반으로', '상태 관리 및 목표 수정'] },
]

/** 03 · 이용화면 미리보기 (Figma UI 페이지 화면 캡처) */
const PREVIEWS = [
  { image: previewTodo, alt: '투두리스트 기본 화면' },
  { image: previewAiInput, alt: 'AI 목표 현재 상태 입력 중 화면' },
  { image: previewAiDone, alt: 'AI 목표 현재 상태 입력 완료 화면' },
]

/**
 * 06 · 결제 관련 안내. 답변 줄바꿈은 Figma 그대로이고 모바일 · 데스크톱이 달라 따로 적습니다.
 * 모바일은 2개씩 묶음(묶음 사이 28, 안 18), 데스크톱은 2개씩 한 줄(줄 사이 52, 칸 사이 28)
 */
interface Faq {
  q: string
  a: string[]
  /** 데스크톱 줄바꿈이 다를 때 */
  desktop?: string[]
  tight?: boolean
}

const FAQ_ROWS: Faq[][] = [
  [
    {
      q: 'Q. 자동으로 또 결제가 되나요?',
      a: ['A. 아니요. 크레딧 충전은 일회성 결제이고 구독이 아닙니다.', '자동 갱신이 없으니 해지할 것도 없습니다.'],
      tight: true,
    },
    {
      q: 'Q. 결제는 누가 처리하나요?',
      a: [
        'A. 주문 처리는 온라인 리셀러인 Paddle.com이 담당하며,',
        '모든 주문에 대해 Paddle.com이 Merchant of Record',
        '입니다. 카드 명세서에는 PADDLE.NET으로 표시됩니다.',
      ],
      // TODO: Figma 모바일은 "Record 입니다", 데스크톱은 "Record입니다"(띄어쓰기 없음)로 달라 각각 그대로 둠
      desktop: [
        'A. 주문 처리는 온라인 리셀러인 Paddle.com이 담당하며,',
        '모든 주문에 대해 Paddle.com이 Merchant of Record입니다.',
        '카드 명세서에는 PADDLE.NET으로 표시됩니다.',
      ],
    },
  ],
  [
    {
      q: 'Q. 환불은 어떻게 받나요?',
      // 10/11 팀 결정 문구 (Figma 문구와 달라 줄바꿈 없이 화면 너비대로 줄이 바뀝니다)
      a: [
        `A. 크레딧을 쓰지 않았다면 사유를 묻지 않고 전액 환불해 드립니다. ${OPERATOR_EMAIL}로 알려주시면 Paddle을 통해 처리됩니다. 서비스 종료일에 남은 크레딧은 신청하지 않아도 환불됩니다.`,
      ],
    },
    {
      q: 'Q. 이미 쓴 크레딧도 환불되나요?',
      a: ['A. 이미 쓴 크레딧은 환불되지 않습니다. 일부만 썼다면 결제 금액에서 쓴 횟수 × 1회당 가격(5회권 $0.80, 10회권 약 $0.67)을 뺀 금액을 돌려드립니다.'],
      tight: true,
    },
  ],
]

/** 07 · 푸터 링크: 약관 · 정책 페이지 3개 + 문의(운영자 메일) */
const FOOTER_LINKS = [
  ...POLICY_DOCS.map((doc) => ({ label: doc.linkLabel, to: doc.path })),
  { label: '문의', to: `mailto:${OPERATOR_EMAIL}` },
]

const FOOTER_LINK_CLASS =
  'text-[14px] leading-[22px] font-bold tracking-[-0.02em] text-[#5480ae] desktop:text-[20px] desktop:leading-[28px] desktop:font-semibold'

/**
 * 랜딩페이지 (Figma 온보딩 > HAMA · 모바일 웹 · 390 / MacBook Pro 14" - 1)
 * 화면 너비 1024px(desktop) 부터 데스크톱 디자인, 그보다 좁으면 모바일 디자인을 너비 가득 보여 줍니다.
 * 1024 ~ 1511px 에서는 데스크톱 디자인을 1512px 기준 비율 그대로 줄여 보여 줍니다(useDesktopZoom).
 * 모바일: 섹션 7개 세로, 사이 간격 24 / 데스크톱: 섹션 6개 사이 40, 푸터 위 10
 * 글자는 Figma 에 텍스트 스타일 이름이 없는 것이 많아 크기 · 굵기 · 행간 · 자간을 직접 적었습니다.
 * Figma 에만 있는 색 #5480AE(강조 파랑) · #F3F3F3(휴대폰 틀)은 토큰 이름이 없어 값으로 씁니다.
 */
export default function LandingPage() {
  const zoom = useDesktopZoom()
  const { hash } = useLocation()

  // 다른 페이지에서 들어오면 맨 위부터 보여 주고, /landing#pricing 처럼 섹션을 지정하면 그 섹션으로 스크롤합니다. (이용약관 푸터의 요금제)
  useEffect(() => {
    if (hash) document.getElementById(hash.slice(1))?.scrollIntoView()
    else window.scrollTo(0, 0)
  }, [hash])

  return (
    <main className="flex min-h-dvh w-full flex-col gap-[24px] bg-white-w1 desktop:gap-[10px]" style={{ zoom }}>
      <div className="flex w-full flex-col gap-[24px] desktop:gap-[40px]">
        {/* 01 · 시작하기 */}
        <LandingSection paddingY={64} tall className="items-center gap-[28px] desktop:gap-[52px]">
          <div className="flex w-full flex-col items-center gap-[10px] desktop:gap-[16px]">
            {/* 하마: 모바일 96(그림 94.81) · 데스크톱 162(그림 160) */}
            <div className="relative size-[96px] desktop:size-[162px]">
              <img
                src={hippo}
                alt=""
                className="absolute top-[0.89px] left-[0.59px] size-[94.81px] desktop:top-[1.5px] desktop:left-[1px] desktop:size-[160px]"
              />
            </div>
            <div className="flex w-full flex-col items-center gap-[10px] desktop:gap-[16px]">
              <h1 className="flex w-full flex-col text-center text-[30px] leading-[42px] font-black tracking-[-0.02em] desktop:text-[56px] desktop:leading-[84px] desktop:font-extrabold">
                <span className="text-black-black">목표 입력하면</span>
                <span className="text-[#5480ae]">오늘의 투두리스트 생성</span>
              </h1>
              <p className="w-full text-center text-[16px] leading-[25px] tracking-[-0.02em] text-black-black desktop:text-[28px] desktop:leading-[34px]">
                장기 목표 달성을 위한 <strong className="font-bold">자동 투두리스트 생성 AI</strong>
              </p>
            </div>
          </div>
          <SignupCta place="hero" />
        </LandingSection>

        {/* 02 · 서비스 이용 흐름 */}
        <LandingSection className="gap-[28px] desktop:gap-[56px]">
          <SectionHeader
            title="서비스 이용 흐름"
            desktopSubtitleLeading={34}
            subtitle={
              <Lines
                lines={['목표를 적으면 투두리스트로 AI가 변환하고', '리포트로 관리합니다.']}
                desktop={['목표를 적으면 투두리스트로 AI가 변환하고 리포트로 관리합니다.']}
              />
            }
          />
          {/* 데스크톱 카드 줄 높이 384 (Figma 고정 높이, 카드 안 내용이 12px 넘침) */}
          <ul className="flex w-full flex-col gap-[18px] desktop:h-[384px] desktop:flex-row desktop:gap-[28px]">
            {FLOW.map((item) => (
              <li
                key={item.title}
                className="flex w-full flex-col items-center gap-[21px] rounded-[20px] p-[24px] desktop:flex-1 desktop:gap-[32px] desktop:p-[40px]"
              >
                <img src={item.image} alt="" className="size-[80px] desktop:size-[140px]" />
                <div className="flex w-full flex-col items-center gap-[8px] text-center tracking-[-0.02em] text-black-black desktop:gap-[12px]">
                  <h3 className="text-[22px] leading-[31px] font-bold desktop:text-[40px] desktop:leading-[48px]">{item.title}</h3>
                  <p className="text-[18px] leading-[28px] desktop:text-[30px] desktop:leading-[42px]">
                    <Lines lines={item.description} />
                  </p>
                </div>
              </li>
            ))}
          </ul>
        </LandingSection>

        {/* 03 · 이용화면 미리보기 (데스크톱: 왼쪽 글 · 오른쪽 휴대폰, 양 끝 정렬) */}
        <LandingSection className="gap-[28px] desktop:flex-row desktop:items-center desktop:justify-between desktop:gap-[20px]">
          <div className="desktop:shrink-0">
            <SectionHeader
              title="이용화면 미리보기"
              gap={18}
              desktopLeft
              subtitle={
                <>
                  할일 조회, 할 일 체크, 내보내기는
                  <br />
                  <strong className="font-bold">크레딧을 쓰지 않습니다.</strong>
                </>
              }
            />
          </div>
          {/*
            휴대폰 틀 · 모바일: 107.69 × 253.97 / 패딩 8.97 / 반경 17.95 / 안쪽 그림자 1.79·4.49 / 화면 반경 12.56 / 사이 13.46
            데스크톱: 240 × 566 / 패딩 20 / 반경 40 / 안쪽 그림자 4·10 / 화면 반경 28 / 사이 30
          */}
          <ul className="flex w-full items-center gap-[13.46px] desktop:w-[780px] desktop:gap-[30px]">
            {PREVIEWS.map((preview) => (
              <li
                key={preview.alt}
                className="flex flex-1 rounded-[17.95px] bg-[#f3f3f3] p-[8.97px] shadow-[inset_-1.79px_-1.79px_4.49px_rgb(0_0_0/0.1),inset_1.79px_1.79px_4.49px_rgb(0_0_0/0.1)] desktop:rounded-[40px] desktop:p-[20px] desktop:shadow-[inset_-4px_-4px_10px_rgb(0_0_0/0.1),inset_4px_4px_10px_rgb(0_0_0/0.1)]"
              >
                <img
                  src={preview.image}
                  alt={preview.alt}
                  className="aspect-[89.74/236.03] w-full rounded-[12.56px] desktop:aspect-[200/526] desktop:rounded-[28px]"
                />
              </li>
            ))}
          </ul>
        </LandingSection>

        {/* 04 · 요금제 */}
        <LandingSection id="pricing" tall className="gap-[28px] desktop:gap-[52px]">
          <SectionHeader
            title="요금제"
            subtitleWeight={400}
            subtitle={
              <>
                HAMA는 월 구독이 아니라,
                <br />
                쓴 횟수만큼만 차감되는 <strong className="font-bold">크레딧 방식</strong>입니다.
              </>
            }
          />
          {/* 카드 테두리 · 모바일: 1회 Black/Gray40, 5회 Brand/Main, 10회 Black/Gray60 / 데스크톱: 1회도 Black/Gray60 */}
          <div className="flex w-full flex-col gap-[18px] desktop:flex-row desktop:items-stretch desktop:gap-[28px]">
            <PlanCard
              title="1회"
              description="한 가지 목표에 집중할 때"
              price="$1"
              perUse="1회당 $1.00"
              borderClass="border-black-gray40 desktop:border-black-gray60"
            />
            <PlanCard
              title="5회"
              badge="추천"
              description="여러 목표를 동시에"
              price="$4"
              perUse="1회당 $0.80"
              saving="1회보다 $1 절약"
              borderClass="border-brand-main"
            />
            <PlanCard
              title={
                <>
                  10회 + <span className="text-category-task">보너스 2회</span>
                </>
              }
              description="더 많은 목표를 동시에"
              price="$8"
              perUse="1회당 약 $0.67"
              saving="5회보다 $1.60 절약"
              borderClass="border-black-gray60"
            />
          </div>
          {/* 10/12 Figma: 카드마다 있던 충전 버튼을 없애고 카드 아래에 시작하기 버튼 하나 */}
          <SignupCta place="pricing" />
        </LandingSection>

        {/* 05 · 제공 혜택 (모바일: 묶음 사이 28 · 안 18 / 데스크톱: 2개씩 한 줄, 줄 사이 52 · 칸 사이 28) */}
        <LandingSection className="gap-[28px] desktop:gap-[52px]">
          <SectionHeader title="1회 결제 시 제공되는 혜택" small />
          <div className="flex w-full flex-col gap-[18px] desktop:flex-row desktop:gap-[28px]">
            <InfoCard title="목표 1개 생성" note="1회 차감">
              <Lines lines={['입력한 목표가 주 단위 마일스톤과 하루 단위', '할일로 나뉘어 저장됩니다.']} />
            </InfoCard>
            <InfoCard title="일정 재계산 1회" note="1회 차감">
              <Lines lines={['일정이 밀렸을 때 남은 기간에 맞추어', '할일을 다시 배치합니다.']} />
            </InfoCard>
          </div>
          <div className="flex w-full flex-col gap-[18px] desktop:flex-row desktop:gap-[28px]">
            <InfoCard title="차감되지 않는 것">
              <Lines lines={['할 일 체크, 목표 조회, 기록 열람, 데이터 내보내기는', '크레딧을 쓰지 않습니다.']} />
            </InfoCard>
            {/* 10/11 팀 결정 문구 (Figma: 구매일로부터 1년동안) */}
            <InfoCard title="유효기간">
              충전한 크레딧은 서비스 종료일(2026년 11월 20일)까지 사용할 수 있습니다. 남은 유료 크레딧은 종료 후 자동으로 환불됩니다.
            </InfoCard>
          </div>
          {/* 데스크톱 높이 126 · 내용 세로 가운데 (Figma 고정 높이) */}
          <InfoCard title="AI 기능에 대한 고지" desktopGrow={false} className="desktop:h-[126px] desktop:justify-center">
            HAMA가 생성하는 일정과 제안은 참고용이며 정확성을 보장하지 않습니다. 의료, 법률, 재무 등 전문 영역의 판단에
            사용하도록 설계 되지 않았습니다.
          </InfoCard>
        </LandingSection>

        {/* 06 · 결제 관련 안내 */}
        <LandingSection tall className="gap-[28px] desktop:gap-[52px]">
          <SectionHeader title="결제 관련 안내" />
          {FAQ_ROWS.map((row) => (
            <div key={row[0].q} className="flex w-full flex-col gap-[18px] desktop:flex-row desktop:gap-[28px]">
              {row.map((faq) => (
                <InfoCard key={faq.q} title={faq.q} boxed={false} tightBody={faq.tight}>
                  <Lines lines={faq.a} desktop={faq.desktop} />
                </InfoCard>
              ))}
            </div>
          ))}
        </LandingSection>
      </div>

      {/*
        07 · 푸터 (배경 없음)
        운영자 정보가 10/11 팀 결정 문구로 길어져 데스크톱에서 두 줄이 되므로 링크 줄은 줄지 않게(shrink-0) 둡니다.
        모바일: 패딩 32·20 / 세로 간격 20 / 링크 14px 700 행간 155% 사이 12 / 운영자 정보 10px 400 자간 -3%
        데스크톱: 패딩 60·100 / 가로 가운데 간격 187 / 링크 20px 600 행간 140% 사이 32 / 운영자 정보 18px 400 자간 -2%
      */}
      <footer className="flex w-full flex-col gap-[20px] px-[20px] py-[32px] desktop:flex-row desktop:items-center desktop:justify-center desktop:gap-[187px] desktop:px-[100px] desktop:py-[60px]">
        <nav className="flex flex-wrap items-start gap-[12px] desktop:shrink-0 desktop:flex-nowrap desktop:items-center desktop:gap-[32px]">
          {FOOTER_LINKS.map(({ label, to }) => (
            <PolicyLink key={label} to={to} className={FOOTER_LINK_CLASS}>
              {label}
            </PolicyLink>
          ))}
        </nav>
        <p className="text-[10px] leading-[15px] tracking-[-0.03em] text-black-black desktop:text-[18px] desktop:leading-[25px] desktop:tracking-[-0.02em]">
          {OPERATOR_INFO}
        </p>
      </footer>
    </main>
  )
}
