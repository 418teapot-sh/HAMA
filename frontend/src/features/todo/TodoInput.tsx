import { useState, type FormEvent } from 'react'
import { PlusIcon } from '../../components/TodoIcons'
import type { TodoCategory } from './categories'

interface TodoInputProps {
  category: TodoCategory
  onSubmit: (content: string) => void
}

/**
 * 투두 입력창 (Figma 투두리스트_추가 > Frame 2085668101)
 * 너비 358 / 높이 48 / 패딩 12·20 / 배경 White/W1 / 테두리 1px 카테고리 색 / 반경 8
 * 글자 Body/B6 Black/Gray80, 오른쪽 ic_add 24 카테고리 색
 * 입력 전 안내 문구(placeholder)는 디자인에 없어 비워 둡니다.
 */
export default function TodoInput({ category, onSubmit }: TodoInputProps) {
  const [content, setContent] = useState('')

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    const text = content.trim()
    if (!text) return
    onSubmit(text)
    setContent('')
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={`flex h-[48px] w-[358px] max-w-full items-start justify-between gap-[10px] rounded-[8px] border bg-white-w1 px-[20px] py-[12px] ${category.borderClass}`}
    >
      <input
        autoFocus
        value={content}
        aria-label={`${category.label} 내용`}
        onChange={(e) => setContent(e.target.value)}
        className="h-[24px] min-w-0 flex-1 text-body-b6 text-black-gray80 outline-none"
      />
      <button type="submit" aria-label={`${category.label} 추가하기`}>
        <PlusIcon className={`size-[24px] ${category.textClass}`} />
      </button>
    </form>
  )
}
