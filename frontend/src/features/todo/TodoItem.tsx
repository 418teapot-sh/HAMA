import Character from '../../components/Character'
import { MeatballIcon, SparkleIcon, TodoCheckIcon } from '../../components/TodoIcons'
import { AI_GENERATED_STYLE, getCategory } from './categories'
import type { LocalTodo } from './mock'

interface TodoItemProps {
  todo: LocalTodo
  /** 체크박스를 눌렀을 때 (완료 ↔ 완료 취소). 없으면 누를 수 없음 (날짜 이동 중) */
  onToggle?: () => void
  /** … 버튼을 눌렀을 때 (더보기 시트 열기). 없으면 누를 수 없음 (날짜 이동 중) */
  onMore?: () => void
}

/**
 * 투두 한 줄 (Figma 투두리스트 > Frame 2085668204 등)
 * - 줄: 가로 가운데 / 간격 8 / 높이 36
 * - 왼쪽: [캐릭터 36] [체크박스 24] 간격 4. 완료하면 캐릭터가 웃는 눈, 체크박스가 카테고리 색으로 채워짐
 * - 오른쪽 (Frame 2085668206): [내용 Body/B7 SemiBold] [아이콘 24] space-between
 *   내용: 미완료 Black/Black, 완료 취소선 + 카테고리 색
 * - 색: 카테고리 색. AI 가 만든 투두는 Brand/Main, 사용자가 AI 목표에 추가한 투두는 AI 카테고리 색(Brand/Main_40)
 * - 아이콘 (Figma 투두리스트_기본상태 10/26 갱신본, 디자이너 답변):
 *   AI 가 만든 투두는 수정 · 삭제할 수 없어 더보기 대신 sparkle (boxicons:sparkle-filled)
 *     완료 전 Black/Gray40, 완료 뒤 Brand/Main
 *   그 밖의 투두(사용자가 AI 목표에 추가한 투두 포함)는 더보기 …(Black/Gray60)
 */
export default function TodoItem({ todo, onToggle, onMore }: TodoItemProps) {
  const { done, aiGenerated } = todo
  const style = aiGenerated ? AI_GENERATED_STYLE : getCategory(todo.category)

  return (
    <li className="flex h-[36px] w-full items-center gap-[8px]">
      <div className="flex shrink-0 items-center gap-[4px]">
        <Character color={style.color} variant={done ? 'done' : 'default'} />
        <button
          type="button"
          role="checkbox"
          aria-checked={done}
          aria-label={`${todo.title} 완료`}
          disabled={!onToggle}
          onClick={onToggle}
        >
          <TodoCheckIcon checked={done} className={`size-[24px] ${done ? style.textClass : 'text-black-gray40'}`} />
        </button>
      </div>
      <div className="flex min-w-0 flex-1 items-center justify-between gap-[8px]">
        <span className={`truncate text-body-b7 font-semibold ${done ? `line-through ${style.textClass}` : 'text-black-black'}`}>
          {todo.title}
        </span>
        {aiGenerated ? (
          // TODO: 완료 뒤 sparkle 을 누르면 뜨는 상태 입력 팝업은 AI 목표 흐름 쪽 작업이라 지금은 표시만 합니다.
          //       상태 입력을 마친 뒤(aiStateEntered) 아이콘 색은 디자인에 없어 확인 필요 (지금은 완료 색 그대로)
          <SparkleIcon className={`size-[24px] shrink-0 ${done ? 'text-brand-main' : 'text-black-gray40'}`} />
        ) : (
          <button type="button" aria-label={`${todo.title} 더보기`} className="shrink-0" disabled={!onMore} onClick={onMore}>
            <MeatballIcon className="size-[24px] text-black-gray60" />
          </button>
        )}
      </div>
    </li>
  )
}
