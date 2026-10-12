import { useState, type FormEvent } from 'react'
import Character from '../../components/Character'
import { CheckIcon } from '../../components/TodoIcons'
import { TODO_CATEGORIES, type TodoCategoryKey } from './categories'

interface TodoEditRowProps {
  title: string
  category: TodoCategoryKey
  onSave: (title: string, category: TodoCategoryKey) => void
}

/**
 * 투두 수정 줄 (Figma 투두리스트_수정 > Frame 2085668202) — 목록에서 그 투두 줄 자리에 들어감
 * 세로 간격 8:
 * - 캐릭터 고르기 (가로 간격 4, 순서 할일 · 일정 · 기타 · AI): 고른 카테고리 = 몸통 80% + 안쪽 테두리 1px, 나머지 = 몸통 20%
 * - 입력 줄 (가로 간격 8, 높이 24): [입력칸 높이 22 · 아래 테두리 1px Black/Gray40 · 글자 Body/B7 SemiBold Black/Gray80] [ic_check 24 Black/Black]
 * TODO: 캐릭터를 눌러 카테고리를 바꾸는 것, 입력칸에 원래 내용을 채워 두는 것은 화면에서 추정한 동작이라 확인 필요
 */
export default function TodoEditRow({ title, category, onSave }: TodoEditRowProps) {
  const [content, setContent] = useState(title)
  const [picked, setPicked] = useState(category)

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const text = content.trim()
    if (!text) return
    onSave(text, picked)
  }

  return (
    <li className="flex w-full flex-col justify-center gap-[8px]">
      <div className="flex h-[36px] items-center gap-[4px]">
        {TODO_CATEGORIES.map((item) => (
          <button
            key={item.key}
            type="button"
            aria-label={item.label}
            aria-pressed={picked === item.key}
            onClick={() => setPicked(item.key)}
          >
            <Character color={item.color} variant={picked === item.key ? 'picked' : 'faded'} />
          </button>
        ))}
      </div>
      <form onSubmit={handleSubmit} className="flex h-[24px] w-full items-start gap-[8px]">
        <input
          autoFocus
          value={content}
          aria-label="투두 내용 수정"
          onChange={(e) => setContent(e.target.value)}
          className="h-[22px] min-w-0 flex-1 border-b border-black-gray40 text-body-b7 font-semibold text-black-gray80 outline-none"
        />
        <button type="submit" aria-label="수정 완료" className="shrink-0">
          <CheckIcon className="size-[24px] text-black-black" />
        </button>
      </form>
    </li>
  )
}
