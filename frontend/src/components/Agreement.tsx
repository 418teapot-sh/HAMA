import Checkbox from './Checkbox'

export interface AgreementItem {
  key: string
  label: string
  required: boolean
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
      <div className="flex w-full flex-col rounded-[8px] bg-white-w1/75 shadow-agreement ring-1 ring-black-gray10 ring-inset">
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
              {/* TODO: ic_right 아이콘 SVG 를 받으면 넣고, 눌렀을 때 약관 상세로 이동할지 확인 필요 */}
              <span className="size-[24px] shrink-0" aria-hidden />
            </div>
          )
        })}
      </div>
    </div>
  )
}
