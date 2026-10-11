import { useEffect, useState } from 'react'

/** 데스크톱 디자인 기준 너비 (Figma MacBook Pro 14" - 1) */
const DESIGN_WIDTH = 1512
/** 데스크톱 디자인을 쓰기 시작하는 너비 (index.css --breakpoint-desktop 과 같은 값) */
const DESKTOP_MIN_WIDTH = 1024

function getZoom() {
  const width = document.documentElement.clientWidth
  return width >= DESKTOP_MIN_WIDTH && width < DESIGN_WIDTH ? width / DESIGN_WIDTH : 1
}

/**
 * 1024 ~ 1511px 화면에서 데스크톱 디자인을 1512px 기준 비율 그대로 줄여 보여 줄 배율.
 * (그보다 좁으면 모바일 디자인, 1512px 이상이면 1 = 원래 크기)
 */
export function useDesktopZoom() {
  const [zoom, setZoom] = useState(getZoom)

  useEffect(() => {
    const onResize = () => setZoom(getZoom())
    window.addEventListener('resize', onResize)
    return () => window.removeEventListener('resize', onResize)
  }, [])

  return zoom
}
