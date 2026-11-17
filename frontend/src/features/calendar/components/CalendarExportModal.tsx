import { useState } from 'react'
import Modal from '@/components/Modal'
import { exportCalendar } from '@/features/calendar/api'

interface CalendarExportModalProps {
  open: boolean
  /** 보고 있는 달 (month 는 1~12). 이 달 1일부터 1년치를 내보냅니다 */
  year: number
  month: number
  onClose: () => void
  /** 끝났을 때 보여 줄 안내 (성공 · 실패 모두) */
  onDone: (message: string) => void
}

/**
 * 캘린더 내보내기 확인 팝업 (Figma 캘린더 내보내기)
 * [내보내기] → .ics 파일 다운로드. 하마에 저장된 일정은 그대로 남습니다.
 */
export default function CalendarExportModal({ open, year, month, onClose, onDone }: CalendarExportModalProps) {
  const [exporting, setExporting] = useState(false)

  const handleExport = async () => {
    if (exporting) return
    setExporting(true)
    try {
      await exportCalendar(year, month)
      onDone('캘린더 파일을 내려받았어요.')
    } catch {
      // 실패 응답이 파일(blob)로 와서 서버 문구를 꺼내지 않고 공통 안내를 보여 줍니다
      onDone('캘린더를 내보내지 못했어요. 잠시 후 다시 시도해 주세요.')
    } finally {
      setExporting(false)
      onClose()
    }
  }

  return (
    <Modal
      open={open}
      title="캘린더를 내보낼까요?"
      description={
        <>
          캘린더를 내보내도
          <br />
          하마에 저장된 일정은 그대로 유지돼요.
        </>
      }
      confirmText={exporting ? '내보내는 중…' : '내보내기'}
      onCancel={onClose}
      onConfirm={() => void handleExport()}
    />
  )
}
