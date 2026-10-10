import { useCallback, useEffect, useRef, useState } from 'react'

/** TODO: 토스트가 떠 있는 시간은 디자인에 없어 확인 필요. 정해지면 바꿉니다. */
const TOAST_DURATION_MS = 3000

/** 토스트를 띄우고 일정 시간 뒤 닫습니다. */
export function useToast() {
  const [message, setMessage] = useState<string | null>(null)
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null)

  const show = useCallback((text: string) => {
    if (timer.current) clearTimeout(timer.current)
    setMessage(text)
    timer.current = setTimeout(() => setMessage(null), TOAST_DURATION_MS)
  }, [])

  useEffect(() => () => {
    if (timer.current) clearTimeout(timer.current)
  }, [])

  return { message, show }
}
