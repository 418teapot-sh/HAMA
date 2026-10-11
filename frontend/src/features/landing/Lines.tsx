import { Fragment } from 'react'

function Joined({ lines }: { lines: string[] }) {
  return lines.map((line, i) => (
    <Fragment key={line}>
      {i > 0 && <br />}
      {line}
    </Fragment>
  ))
}

/**
 * 여러 줄 문장. Figma 에서 줄을 바꿔 둔 자리에서 그대로 줄을 바꿉니다.
 * 데스크톱 Figma 의 줄바꿈이 다르면 desktop 에 따로 줍니다.
 * (화면이 좁아 한 줄에 다 안 들어가면 그 안에서 한 번 더 줄이 바뀝니다)
 */
export default function Lines({ lines, desktop }: { lines: string[]; desktop?: string[] }) {
  if (!desktop) return <Joined lines={lines} />
  return (
    <>
      <span className="desktop:hidden">
        <Joined lines={lines} />
      </span>
      <span className="hidden desktop:inline">
        <Joined lines={desktop} />
      </span>
    </>
  )
}
