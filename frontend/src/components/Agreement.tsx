import { Link } from 'react-router'
import icRight from '../assets/icons/ic_right.svg'
import Checkbox from './Checkbox'

export interface AgreementItem {
  key: string
  label: string
  required: boolean
  /** 오른쪽 화살표(ic_right)를 누르면 새 탭으로 열 약관 페이지 주소. 없으면 화살표는 모양만 */
  href?: string
}

interface AgreementProps {
  items: AgreementItem[]
  checked: Record<string, boolean>
  onChange: (next: Record<string, boolean>) => void
}

/**
 * 약관 동의 (Figma 컴포넌트: Agreement)
 * - 바깥: 좌우 패딩 20 · 위아래 10, 그림자 Y15 흐림40 #CECECE 8%
 * - 카드: 반경 8 / 테두리 1px Black/Gray10(안쪽, 크기에 포함) / 배경 Card_Back(#FCFCFC 75%)
 * - 전체 동의 행: 패딩 8·12, 반경 8 / 문구 Body/B7 (14/400/150%) Black/Black
 * - 항목 행: 패딩 위 8·좌우 10·아래 4 (마지막 행만 아래 8), 반경 8, space-between
 *   문구 Body/B8 (14/400/150%) Black/Gray80, 오른쪽 ic_right
 * - 체크박스 묶음: 오른쪽 패딩 4 (체크박스 24 + 4 = 28)
 *
 * Figma 에는 같은 프레임에 배경 흐림(20)도 있지만, 흐림이 걸린 프레임에 배경색이 없어 화면에 보이지 않으므로 넣지 않았습니다.
 */
export default function Agreement({ items, checked, onChange }: AgreementProps) {
  const allChecked = items.every((item) => checked[item.key])

  function toggleAll(next: boolean) {
    onChange(Object.fromEntries(items.map((item) => [item.key, next])))
  }

  return (
    <div className="w-full px-[20px] py-[10px]">
      <div className="flex w-full flex-col rounded-[8px] bg-card-back shadow-shadow ring-1 ring-black-gray10 ring-inset">
        <div className="flex w-full items-center rounded-[8px] px-[12px] py-[8px]">
          <label htmlFor="agree-all" className="flex w-full items-center">
            <span className="flex pr-[4px]">
              <Checkbox id="agree-all" checked={allChecked} onChange={toggleAll} />
            </span>
            <span className="text-body-b7 text-black-black">약관 전체 동의</span>
          </label>
        </div>

        {items.map((item, index) => {
          const isLast = index === items.length - 1
          const inputId = `agree-${item.key}`
          return (
            <div
              key={item.key}
              className={`flex w-full items-center justify-between rounded-[8px] px-[10px] pt-[8px] ${isLast ? 'pb-[8px]' : 'pb-[4px]'}`}
            >
              <label htmlFor={inputId} className="flex flex-1 items-center">
                <span className="flex pr-[4px]">
                  <Checkbox
                    id={inputId}
                    checked={!!checked[item.key]}
                    onChange={(next) => onChange({ ...checked, [item.key]: next })}
                  />
                </span>
                <span className="text-body-b8 text-black-gray80">{item.label}</span>
              </label>
              {/* 약관 페이지는 새 탭으로 열어, 쓰던 회원가입 입력이 사라지지 않게 합니다. */}
              {item.href ? (
                <Link to={item.href} target="_blank" rel="noopener noreferrer" aria-label={`${item.label} 내용 보기`} className="shrink-0">
                  <img src={icRight} alt="" className="size-[24px]" />
                </Link>
              ) : (
                <img src={icRight} alt="" className="size-[24px] shrink-0" />
              )}
            </div>
          )
        })}
      </div>
    </div>
  )
}
