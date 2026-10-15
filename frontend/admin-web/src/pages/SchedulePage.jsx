import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { api } from '../api.js'

const HOUR_W = 150 // 1시간 칸 너비(px)
const MIN_W = HOUR_W / 60
const POLL_MS = 5000

const today = () => new Date().toLocaleDateString('sv-SE') // yyyy-MM-dd
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x.toLocaleDateString('sv-SE')
}
const dayStart = (d) => new Date(`${d}T00:00:00`).getTime()
const hm = (ms) => new Date(ms).toTimeString().slice(0, 5)
const pad = (n) => String(n).padStart(2, '0')

/**
 * 관리자 > LIVE > 스케줄 관리
 * 편성 관리에 등록된 편성을 하루 단위 타임라인으로 보여준다. (조회 전용)
 * 블록: 방송 중(편성 시간 + 채널 LIVE) 빨강 / 그 외 회색. 클릭 시 해당 채널 모니터링으로 이동.
 */
export default function SchedulePage() {
  const [date, setDate] = useState(today())
  const [channels, setChannels] = useState([])
  const [schedules, setSchedules] = useState([])
  const [searchType, setSearchType] = useState('code')
  const [keyword, setKeyword] = useState('')
  const [applied, setApplied] = useState({ type: 'code', keyword: '' })
  const [error, setError] = useState('')
  const now = useNow()
  const navigate = useNavigate()
  const scrollRef = useRef(null)
  const pickerRef = useRef(null)

  const loadChannels = () => api.channels.list().then(setChannels).catch((e) => setError(e.message))

  useEffect(() => {
    loadChannels()
    const t = setInterval(loadChannels, POLL_MS) // LIVE/OFFLINE 상태 갱신
    return () => clearInterval(t)
  }, [])

  useEffect(() => {
    api.schedules.list(date, date)
      .then((list) => {
        setSchedules(list)
        scrollToFocus(list)
      })
      .catch((e) => setError(e.message))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  // 오늘은 현재 시각 1시간 전, 다른 날은 첫 편성(없으면 09시) 근처로 스크롤
  const scrollToFocus = (list) => {
    const el = scrollRef.current
    if (!el) return
    const start = dayStart(date)
    const first = list.length ? Math.min(...list.map((s) => new Date(s.startAt).getTime())) : start + 9 * 3600000
    const focus = date === today() ? Date.now() - 3600000 : first - 1800000
    el.scrollLeft = Math.max(0, ((focus - start) / 60000) * MIN_W)
  }

  const start = dayStart(date)
  const end = start + 24 * 3600000
  const nowX = now >= start && now < end ? ((now - start) / 60000) * MIN_W : null

  const kw = applied.keyword.trim().toLowerCase()
  const rows = kw ? channels.filter((c) => c[applied.type].toLowerCase().includes(kw)) : channels

  const openPicker = () => {
    const el = pickerRef.current
    if (el?.showPicker) el.showPicker()
    else el?.click()
  }

  return (
    <>
      <h2>스케줄 관리</h2>

      <div className="tl-toolbar">
        <div className="tl-date">
          <button className="icon" onClick={() => setDate(addDays(date, -1))} aria-label="이전 날">‹</button>
          <strong>{date}</strong>
          <button className="icon" onClick={() => setDate(addDays(date, 1))} aria-label="다음 날">›</button>
        </div>
        <button className="ghost" onClick={() => setDate(today())}>오늘</button>
        <span className="tl-picker">
          <button className="ghost" onClick={openPicker} aria-label="날짜 선택">📅</button>
          <input ref={pickerRef} type="date" value={date} onChange={(e) => e.target.value && setDate(e.target.value)} />
        </span>
        <span className="spacer" />
        {error && <span className="error">{error}</span>}
        <form className="tl-search" onSubmit={(e) => { e.preventDefault(); setApplied({ type: searchType, keyword }) }}>
          <select value={searchType} onChange={(e) => setSearchType(e.target.value)}>
            <option value="code">채널 ID</option>
            <option value="name">채널명</option>
          </select>
          <input value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="검색어를 입력하세요" />
          <button type="submit" aria-label="검색">🔍</button>
        </form>
      </div>

      <div className="tl">
        <div className="tl-left">
          <div className="tl-head">채널명</div>
          {rows.map((c) => (
            <div key={c.id} className="tl-ch">
              <span className={`badge ${c.status === 'LIVE' ? 'live' : ''}`}>{c.status}</span>
              <strong>{c.name}</strong>
            </div>
          ))}
        </div>

        <div className="tl-right" ref={scrollRef}>
          <div className="tl-canvas" style={{ width: 24 * HOUR_W }}>
            <div className="tl-head">
              {Array.from({ length: 24 }, (_, h) => (
                <span key={h} className="tl-hour" style={{ left: h * HOUR_W }}>{pad(h)}:00</span>
              ))}
              {nowX !== null && <span className="tl-now-label" style={{ left: nowX }}>{hm(now)}</span>}
            </div>

            {rows.map((c) => (
              <div key={c.id} className="tl-row">
                {schedules.filter((s) => s.channelId === c.id).map((s) => {
                  const sStart = new Date(s.startAt).getTime()
                  const sEnd = new Date(s.endAt).getTime()
                  const left = ((Math.max(sStart, start) - start) / 60000) * MIN_W
                  const width = ((Math.min(sEnd, end) - Math.max(sStart, start)) / 60000) * MIN_W
                  const onAir = c.status === 'LIVE' && now >= sStart && now < sEnd
                  return (
                    <button key={s.id} className={`tl-block ${onAir ? 'on-air' : ''}`}
                      style={{ left, width: Math.max(width, 4) }}
                      title={`${s.title} (${hm(sStart)} - ${hm(sEnd)}) · 클릭 시 모니터링`}
                      onClick={() => navigate(`/live/monitoring?channelId=${c.id}`)}>
                      <span className="tl-block-title">{onAir && <span className="tl-onair-icon">((•))</span>}{s.title}</span>
                      <span className="tl-block-time">{hm(sStart)} - {hm(sEnd)}</span>
                    </button>
                  )
                })}
              </div>
            ))}

            {nowX !== null && <div className="tl-now" style={{ left: nowX }} />}
          </div>
        </div>
      </div>
      {rows.length === 0 && <p className="muted center">{kw ? '검색 결과가 없습니다.' : '등록된 채널이 없습니다.'}</p>}
    </>
  )
}

function useNow() {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 30000)
    return () => clearInterval(t)
  }, [])
  return now
}
