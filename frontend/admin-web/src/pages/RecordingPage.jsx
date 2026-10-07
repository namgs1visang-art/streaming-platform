import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { VIEWER_URL } from '../lib/useChatSocket.js'

const fmt = (s) => s?.replace('T', ' ').slice(0, 16)
const dur = (sec) => {
  if (sec == null) return '-'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = sec % 60
  return `${h ? `${h}:` : ''}${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
}
const size = (b) => (b == null ? '-' : b > 1024 ** 3 ? `${(b / 1024 ** 3).toFixed(2)} GB` : `${(b / 1024 ** 2).toFixed(1)} MB`)
const STATUS = { NONE: '녹화 안 함', RECORDING: '녹화 중', READY: '다시보기 가능', FAILED: '실패' }

/**
 * 관리자 > VOD > 방송 녹화 관리
 * OBS 송출 1회 = 방송 1건. SRS DVR 이 녹화한 mp4 + 그 시간대 채팅/퀴즈로 다시보기 제공.
 */
export default function RecordingPage() {
  const [channels, setChannels] = useState([])
  const [channelId, setChannelId] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ items: [], total: 0, size: 20 })
  const [error, setError] = useState('')

  const load = (p = page) =>
    api.broadcasts.list({ channelId, page: p, size: 20 }).then(setData).catch((e) => setError(e.message))

  useEffect(() => { api.channels.list().then(setChannels).catch(() => {}) }, [])
  useEffect(() => {
    setPage(0)
    load(0)
    const t = setInterval(() => load(), 10000) // 녹화 상태 갱신
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelId])

  const remove = async (id) => {
    if (!confirm('방송 이력을 삭제할까요? (녹화 파일은 SRS 폴더에 남습니다)')) return
    try {
      await api.broadcasts.remove(id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const pages = Math.max(1, Math.ceil(data.total / (data.size || 20)))

  return (
    <>
      <h2>방송 녹화 관리</h2>
      <div className="card form-row">
        <select value={channelId} onChange={(e) => setChannelId(e.target.value)}>
          <option value="">전체 채널</option>
          {channels.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <span className="muted">녹화 여부는 스케줄의 [녹화] 옵션을 따릅니다. (편성 없이 송출하면 viewer-api app.recording.default-enabled)</span>
      </div>
      {error && <p className="error">{error}</p>}

      <table className="table">
        <thead>
          <tr><th>채널</th><th>제목</th><th>방송 시간</th><th>길이</th><th>녹화</th><th>파일</th><th>채팅</th><th>퀴즈</th><th></th></tr>
        </thead>
        <tbody>
          {data.items.map((b) => (
            <tr key={b.id}>
              <td>{b.channelName}</td>
              <td>{b.title}{b.scheduleTitle && <div className="muted">편성: {b.scheduleTitle}</div>}</td>
              <td className="nowrap">{fmt(b.startedAt)} ~ {b.endedAt ? b.endedAt.slice(11, 16) : <span className="badge live">LIVE</span>}</td>
              <td>{dur(b.durationSec)}</td>
              <td>
                <span className={`badge rec-${b.recordingStatus}`}>{STATUS[b.recordingStatus]}</span>
                {b.recordingError && <div className="error small">{b.recordingError}</div>}
              </td>
              <td>{size(b.fileSizeBytes)}</td>
              <td>{b.chatCount.toLocaleString()}</td>
              <td>{b.quizCount}</td>
              <td className="actions">
                {b.recordingStatus === 'READY' && (
                  <>
                    <a className="ghost-link" href={`${VIEWER_URL}/replay/${b.id}`} target="_blank" rel="noreferrer">다시보기</a>
                    <a className="ghost-link" href={b.videoUrl} target="_blank" rel="noreferrer">mp4</a>
                  </>
                )}
                <button className="ghost danger" onClick={() => remove(b.id)}>삭제</button>
              </td>
            </tr>
          ))}
          {data.items.length === 0 && <tr><td colSpan="9" className="muted center">방송 이력이 없습니다. OBS 로 송출하면 자동으로 기록됩니다.</td></tr>}
        </tbody>
      </table>

      <div className="toolbar">
        <button className="ghost" disabled={page === 0} onClick={() => { setPage(page - 1); load(page - 1) }}>◀ 이전</button>
        <span>{page + 1} / {pages}</span>
        <button className="ghost" disabled={page + 1 >= pages} onClick={() => { setPage(page + 1); load(page + 1) }}>다음 ▶</button>
      </div>
    </>
  )
}
