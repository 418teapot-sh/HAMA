import LandingButton from './LandingButton'

/** 모바일 버튼 · 안내 문구 사이 간격과 안내 문구 글자 (데스크톱은 둘 다 사이 8 · 18px 행간 140%(25px)) */
const MOBILE = {
  // 01 · 시작하기: 사이 5 / 13px 행간 155%(20px)
  hero: { gap: 'gap-[5px]', note: 'text-[13px] leading-[20px]' },
  // 04 · 요금제 아래: 사이 8 / Body/B8 14px 행간 150%(21px)
  pricing: { gap: 'gap-[8px]', note: 'text-[14px] leading-[21px]' },
}

/**
 * 회원가입하고 시작하기 버튼 + "첫 1회는 무료 | 자동 결제 없음" (Figma 01 · 시작하기 / 04 · 요금제 아래)
 * 버튼은 모바일 너비 가득 · 데스크톱 hero 크기, 안내 문구 400 가운데 Black/Gray60
 * TODO: 누르면 이동할 곳은 회의 후 연결 (지금은 모양만)
 */
export default function SignupCta({ place }: { place: keyof typeof MOBILE }) {
  const mobile = MOBILE[place]
  return (
    <div className={`flex w-full flex-col items-center ${mobile.gap} desktop:gap-[8px]`}>
      <LandingButton>
        회원가입하고 시작하기
      </LandingButton>
      <p className={`text-center text-black-gray60 ${mobile.note} desktop:text-[18px] desktop:leading-[25px]`}>
        첫 1회는 무료 | 자동 결제 없음
      </p>
    </div>
  )
}
