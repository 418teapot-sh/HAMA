import icCheckBoxOff from '../assets/icons/ic_checkBox_off.svg'
import icCheckBoxOn from '../assets/icons/ic_checkBox_on.svg'

interface CheckboxProps {
  id: string
  checked: boolean
  onChange: (checked: boolean) => void
}

/**
 * 체크박스 (Figma 컴포넌트: Checkbox, off = ic_checkBox / on = ic_checkBox_fill, 24×24)
 * 실제 입력은 화면에서 숨긴 기본 체크박스가 받고(키보드·스크린리더용), 보이는 건 Figma 아이콘입니다.
 *
 * TODO: #47 의 공용 Checkbox 가 develop 에 머지되면 그걸로 교체합니다.
 */
export default function Checkbox({ id, checked, onChange }: CheckboxProps) {
  return (
    <span className="relative flex size-[24px] shrink-0">
      <input
        id={id}
        type="checkbox"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
        className="sr-only"
      />
      <img src={checked ? icCheckBoxOn : icCheckBoxOff} alt="" className="size-[24px]" />
    </span>
  )
}
