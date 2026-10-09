interface CheckboxProps {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
}

/**
 * 체크박스 (Figma 컴포넌트: Checkbox, Property off/on, 아이콘 ic_checkBox 24×24)
 *
 * TODO: ic_checkBox(off·on) 아이콘 SVG 를 Figma 에서 받으면 아이콘으로 교체합니다.
 *       지금은 동작 확인용으로 브라우저 기본 체크박스를 24×24 영역 가운데에 둡니다(디자인과 다름).
 * TODO: #47 의 공용 Checkbox 가 develop 에 머지되면 그걸로 교체합니다.
 */
export default function Checkbox({ id, checked, onChange }: CheckboxProps) {
  return (
    <span className="flex size-[24px] shrink-0 items-center justify-center">
      <input id={id} type="checkbox" checked={checked} onChange={(e) => onChange(e.target.checked)} />
    </span>
  )
}
