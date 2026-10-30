import Character from '../../components/Character'
import { MeatballIcon, SparkleIcon, TodoCheckIcon } from '../../components/TodoIcons'
import { AI_GENERATED_STYLE, getCategory } from './categories'
import type { TodoView } from './types'

interface TodoItemProps {
  todo: TodoView
  /** 체크박스를 눌렀을 때 (완료 ↔ 완료 취소). 없으면 누를 수 없음 (날짜 이동 중 · 일정) */
  onToggle?: () => void
  /** … 버튼을 눌렀을 때 (더보기 시트 열기). 없으면 누를 수 없음 (날짜 이동 중) */
  onMore?: () => void
  /** AI 가 만든 투두의 sparkle 을 눌렀을 때 (AI 목표 상태수정 시트). 없으면 누를 수 없음 */
  onSparkle?: () => void
}

/**
 * 투두 한 줄 (Figma 투두리스트 > Frame 2085668204 등)
 * - 줄: 가로 가운데 / 간격 8 / 높이 36
 * - 왼쪽: [캐릭터 36] [체크박스 24] 간격 4. 완료하면 캐릭터가 웃는 눈, 체크박스가 카테고리 색으로 채워짐
 * - 시간이 있으면 왼쪽과 오른쪽 사이에 시간 (Figma 일정 줄 '15:00': Body/B7 SemiBold, 카테고리 색)
 * - 오른쪽: [내용 Body/B7 SemiBold] [아이콘 24] space-between
 *   내용: 미완료 Black/Black, 완료 취소선 + 카테고리 색
 *   현재 상태를 입력한 목표 투두는 내용 아래에 작게 상태 메모 (Figma AI 목표 현재 상태 입력 완료, Black/Gray40)
 * - 색: 카테고리 색. AI 가 만든 투두는 Brand/Main, 사용자가 AI 목표에 추가한 투두는 AI 카테고리 색(Brand/Main_40)
 * - 아이콘: AI 가 만든 투두는 수정 · 삭제할 수 없어 더보기 대신 sparkle (누르면 AI 목표 상태수정 시트)
 *     완료 전 Black/Gray40, 완료 뒤 Brand/Main
 *   그 밖의 투두는 더보기 …(Black/Gray60)
 */
export default function TodoItem({ todo, onToggle, onMore, onSparkle }: TodoItemProps) {
  const { done, aiGenerated } = todo
  const style = aiGenerated ? AI_GENERATED_STYLE : getCategory(todo.category)
  const note = todo.category === 'ai' ? todo.statusNote : null

  return (
    <li className={`flex w-full items-center gap-[8px] ${note ? 'min-h-[36px] py-[2px]' : 'h-[36px]'}`}>
      <div className="flex shrink-0 items-center gap-[4px]">
        <Character color={style.color} variant={done ? 'done' : 'default'} />
        <button
          type="button"
          role="checkbox"
          aria-checked={done}
          aria-label={`${todo.title} ${done ? '완료 취소' : '완료'}`}
          disabled={!onToggle}
          onClick={onToggle}
        >
          <TodoCheckIcon checked={done} className={`size-[24px] ${done ? style.textClass : 'text-black-gray40'}`} />
        </button>
      </div>
      {todo.time && <span className={`shrink-0 text-body-b7 font-semibold ${style.textClass}`}>{todo.time}</span>}
      <div className="flex min-w-0 flex-1 items-center justify-between gap-[8px]">
        <div className="flex min-w-0 flex-col">
          <span className={`truncate text-body-b7 font-semibold ${done ? `line-through ${style.textClass}` : 'text-black-black'}`}>
            {todo.title}
          </span>
          {note && <span className="truncate text-[12px] leading-[16px] text-black-gray40">{note}</span>}
        </div>
        {aiGenerated ? (
          <button
            type="button"
            aria-label={`${todo.title} AI 목표 상태수정`}
            className="shrink-0"
            disabled={!onSparkle}
            onClick={onSparkle}
          >
            <SparkleIcon className={`size-[24px] ${done ? 'text-brand-main' : 'text-black-gray40'}`} />
          </button>
        ) : (
          <button type="button" aria-label={`${todo.title} 더보기`} className="shrink-0" disabled={!onMore} onClick={onMore}>
            <MeatballIcon className="size-[24px] text-black-gray60" />
          </button>
        )}
      </div>
    </li>
  )
}
