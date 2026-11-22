import { useState, type ReactNode } from 'react'
import { useLocation } from 'react-router'
import { paths } from '@/paths'
import icCloudUpload from '@/assets/icons/ic_cloud_upload.svg'
import hippo from '@/assets/landing/hippo.webp'
import calendarCapture from '@/assets/notice-calendar-export.png'
import Modal from './Modal'

type Notice = { id: string; title: string; image?: ReactNode; description: ReactNode }

/** 순서대로 하나씩 띄웁니다. */
const NOTICES: Notice[] = [
  {
    id: 'shutdown',
    title: '서비스 종료 안내',
    image: (
      <span className="relative inline-block">
        <img src={hippo} alt="" className="size-[96px]" />
        <span
          aria-hidden
          className="absolute -right-[6px] top-[18px] origin-[70%_70%] text-[28px] motion-safe:animate-[hama-wave_1.6s_ease-in-out_infinite]"
        >
          👋
        </span>
      </span>
    ),
    description: (
      <>
        HAMA는 11월 23일(월) 오후 2시까지 운영돼요.
        <br />
        크레딧 충전(결제)은 지금부터 중단돼요.
        <br />
        그동안 이용해 주셔서 감사합니다.
      </>
    ),
  },
  {
    id: 'calendar-export',
    title: '일정을 내 캘린더로 옮겨 보세요',
    image: (
      // 실제 투두리스트 캘린더 윗부분 캡처. 구름 버튼 위치(가로 33% · 세로 33%)에 빨간 원을 겹칩니다.
      <span className="relative mb-[8px] block w-full overflow-hidden rounded-[12px] border border-black-gray20">
        <img src={calendarCapture} alt="투두리스트 캘린더 윗부분, 연월 옆의 구름 버튼" className="block w-full" />
        <span
          aria-hidden
          className="absolute left-[33.3%] top-[33%] size-[40px] -translate-x-1/2 -translate-y-1/2 rounded-full border-[3px] border-red-500 motion-safe:animate-pulse"
        />
      </span>
    ),
    description: (
      <>
        투두리스트 캘린더의
        <img src={icCloudUpload} alt="구름" className="mx-[2px] inline size-[18px] align-text-bottom" />
        버튼을 누르면
        <br />
        일정 파일(.ics)을 받아 휴대폰 · 구글 캘린더에
        <br />
        추가할 수 있어요.
      </>
    ),
  },
]

const key = (id: string) => `noticeHiddenDate:${id}`
const today = () => new Date().toLocaleDateString('sv') // YYYY-MM-DD (로컬 날짜)

const hiddenToday = (id: string) => {
  try {
    return localStorage.getItem(key(id)) === today()
  } catch {
    return false
  }
}

/** 공지를 띄울 화면. 다른 화면에서는 안 띄웁니다. */
const SHOW_ON: string[] = [paths.landing, paths.home, paths.mypage]

/**
 * 공지 팝업. 라우터 맨 위에 한 번만 붙어 있어서, 닫은 공지는 새로고침 전까지 다시 안 뜹니다.
 * "오늘 하루 보지 않기"를 누르면 그 공지는 그날 다시 안 띄웁니다.
 */
export default function Notices() {
  const { pathname } = useLocation()
  const [queue, setQueue] = useState(() => NOTICES.filter((n) => !hiddenToday(n.id)))
  const current = queue[0]
  if (!current || !SHOW_ON.includes(pathname)) return null

  const next = () => setQueue((q) => q.slice(1))
  const hideToday = () => {
    try {
      localStorage.setItem(key(current.id), today())
    } catch {
      // 저장이 막힌 브라우저면 닫기만
    }
    next()
  }

  return (
    <Modal
      key={current.id}
      open
      title={
        <span className="flex flex-col items-center gap-[12px]">
          {current.image}
          {current.title}
        </span>
      }
      description={current.description}
      // Modal 은 바깥 클릭도 onCancel 이라, 바깥 클릭이 "오늘 하루 보지 않기"가 되지 않도록 닫기 쪽에 둡니다.
      cancelText="닫기"
      confirmText="오늘 하루 보지 않기"
      onCancel={next}
      onConfirm={hideToday}
    />
  )
}
