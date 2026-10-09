/**
 * 하단 탭 아이콘 (Figma Tap 컴포넌트 안의 아이콘, 24×24)
 * Figma 에서 "SVG로 복사"한 경로 그대로입니다. 선택된 탭은 #042558(Brand/Main), 나머지는 #A2A2A2(Black/Gray40) 로
 * 모양은 같고 색만 달라서, 색은 currentColor 로 두고 TabItem 에서 글자색으로 정합니다.
 */
interface IconProps {
  className?: string
}

/** boxicons:home-alt-2-filled */
export function HomeIcon({ className }: IconProps) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden className={className}>
      <path
        d="M12.71 2.29C12.6175 2.1973 12.5076 2.12375 12.3866 2.07357C12.2657 2.02339 12.136 1.99756 12.005 1.99756C11.874 1.99756 11.7444 2.02339 11.6234 2.07357C11.5024 2.12375 11.3925 2.1973 11.3 2.29L3.29002 10.29C3.19734 10.3834 3.12401 10.4943 3.07425 10.6161C3.02448 10.7379 2.99926 10.8684 3.00002 11V20C3.00002 21.1 3.90002 22 5.00002 22H8.00002C8.55002 22 9.00002 21.55 9.00002 21V14H15V21C15 21.55 15.45 22 16 22H19C20.1 22 21 21.1 21 20V11C21 10.73 20.89 10.48 20.71 10.29L12.71 2.29Z"
        fill="currentColor"
      />
    </svg>
  )
}

/** ic_check */
export function TodoIcon({ className }: IconProps) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden className={className}>
      <path
        d="M9.27603 15.3593C9.08959 15.5454 8.78749 15.5453 8.60114 15.3591L5.63026 12.3913C5.25838 12.0198 4.65539 12.0191 4.28219 12.3893C3.90713 12.7614 3.90568 13.3674 4.27942 13.7408L8.26346 17.7207C8.63628 18.0931 9.24073 18.0931 9.61355 17.7207L19.7212 7.62354C20.0929 7.25213 20.0929 6.64996 19.7212 6.27856C19.3495 5.90727 18.7469 5.90713 18.3751 6.27824L9.27603 15.3593Z"
        fill="currentColor"
      />
    </svg>
  )
}

/** boxicons:file-report-filled */
export function ReportIcon({ className }: IconProps) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden className={className}>
      <path
        d="M14.71 2.29C14.6166 2.19732 14.5057 2.12399 14.3839 2.07423C14.2621 2.02447 14.1316 1.99924 14 2H6C4.9 2 4 2.9 4 4V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V8C20 7.73 19.89 7.48 19.71 7.29L14.71 2.29ZM9 19H7V13H9V19ZM13 19H11V11H13V19ZM17 19H15V15H17V19ZM13 9V3.5L18.5 9H13Z"
        fill="currentColor"
      />
    </svg>
  )
}

/** boxicons:user-filled */
export function MyPageIcon({ className }: IconProps) {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden className={className}>
      <path
        d="M12 2C10.6739 2 9.40215 2.52678 8.46447 3.46447C7.52678 4.40215 7 5.67392 7 7C7 8.32608 7.52678 9.59785 8.46447 10.5355C9.40215 11.4732 10.6739 12 12 12C13.3261 12 14.5979 11.4732 15.5355 10.5355C16.4732 9.59785 17 8.32608 17 7C17 5.67392 16.4732 4.40215 15.5355 3.46447C14.5979 2.52678 13.3261 2 12 2ZM4 22H20C20.55 22 21 21.55 21 21V20C21 16.14 17.86 13 14 13H10C6.14 13 3 16.14 3 20V21C3 21.55 3.45 22 4 22Z"
        fill="currentColor"
      />
    </svg>
  )
}
