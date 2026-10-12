import Character from '../../components/Character'
import { PlusIcon } from '../../components/TodoIcons'
import { TODO_CATEGORIES, type TodoCategoryKey } from './categories'

interface CategoryBarProps {
  selected: TodoCategoryKey | null
  onSelect: (key: TodoCategoryKey | null) => void
}

/**
 * 투두 카테고리 바 (Figma 투두리스트 > Frame 2085668214, 순서 할일 · 일정 · 기타 · AI)
 * - 바: 가로 space-between / 패딩 8·40 / 반경 100 / 배경 Card_Back / 테두리 1px Card_Back / Card_Shadow + 배경 흐림
 * - 항목: 세로 가운데 / 패딩 8·12 → [ic_add 24] [캐릭터 36] [이름 Body/B9 카테고리 색]
 * - 고른 항목(투두리스트_추가): 배경·테두리 1px 카테고리 색, 반경 100, Card_Shadow + 배경 흐림,
 *   + 와 이름 White/W1, 캐릭터 몸통 #FCFCFC · 눈 카테고리 색
 * 같은 항목을 다시 누르면 고른 것을 풉니다.
 */
export default function CategoryBar({ selected, onSelect }: CategoryBarProps) {
  return (
    <div className="flex w-full items-start justify-between rounded-[100px] border border-card-back bg-card-back px-[40px] py-[8px] shadow-card-shadow backdrop-blur-[10px]">
      {TODO_CATEGORIES.map((category) => {
        const isSelected = selected === category.key
        return (
          <button
            key={category.key}
            type="button"
            aria-pressed={isSelected}
            aria-label={`${category.label} 추가`}
            onClick={() => onSelect(isSelected ? null : category.key)}
            className={`flex w-[62px] flex-col items-center justify-center px-[12px] py-[8px] ${
              isSelected ? `rounded-[100px] border shadow-card-shadow backdrop-blur-[10px] ${category.fillClass}` : ''
            }`}
          >
            <PlusIcon className={`size-[24px] ${isSelected ? 'text-white-w1' : 'text-black-black'}`} />
            <Character color={category.color} variant={isSelected ? 'selected' : 'default'} />
            <span className={`w-full text-center text-body-b9 ${isSelected ? 'text-white-w1' : category.textClass}`}>
              {category.label}
            </span>
          </button>
        )
      })}
    </div>
  )
}

/**
 * 고른 카테고리 아래 꼭지 (Figma Frame 2085668221: 가로 space-between / 좌우 패딩 40 / 높이 12, 칸 62)
 * 고른 칸에만 삼각형(Polygon 1, 카테고리 색)이 보입니다.
 */
export function CategoryPointer({ selected }: { selected: TodoCategoryKey }) {
  return (
    <div className="flex h-[12px] w-full items-start justify-between px-[40px]" aria-hidden>
      {TODO_CATEGORIES.map((category) => (
        <span key={category.key} className="flex h-[12px] w-[62px] justify-center">
          {category.key === selected && (
            <svg width="17" height="13" viewBox="0 0 17 13" fill="none" className={category.textClass}>
              <path
                d="M6.74762 0.779922C7.54822 -0.260093 9.11664 -0.260093 9.91724 0.779922L16.246 9.0012C17.2584 10.3163 16.3208 12.2212 14.6612 12.2212H2.00368C0.344007 12.2212 -0.593522 10.3163 0.418872 9.0012L6.74762 0.779922Z"
                fill="currentColor"
              />
            </svg>
          )}
        </span>
      ))}
    </div>
  )
}
