import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'

const today = () => new Date().toLocaleDateString('sv-SE')
const time = (s) => s.slice(11, 16)

/** 오늘 편성표 - 채널별로 묶어서 표시 (같은 시간대 동시 방송 확인용) */
export default function ScheduleBoardPage() {
  const [date, setDate] = useState(today())
  const [items, setItems] = useState([])

  useEffect(() => {
    fetch(`/api/schedules?date=${date}`).then((r) => r.json()).then(setItems).catch(() => {})
  }, [date])

  const byChannel = useMemo(() => {
    const map = {}
    items.forEach((s) => {
      (map[s.channelCode] ||= { name: s.channelName, list: [] }).list.push(s)
    })
    return Object.entries(map)
  }, [items])

  return (
    <main className="container">
      <h2>편성표</h2>
      <input type="date" value={date} onChange={(e) => setDate(e.target.value)} />
      <div className="board">
        {byChannel.map(([code, ch]) => (
          <div key={code} className="board-col">
            <Link to={`/watch/${code}`} className="board-title">{ch.name}</Link>
            {ch.list.map((s) => (
              <div key={s.id} className="board-item">
                <span>{time(s.startAt)} ~ {time(s.endAt)}</span>
                <strong>{s.title}</strong>
              </div>
            ))}
          </div>
        ))}
      </div>
      {items.length === 0 && <p className="muted">편성된 방송이 없습니다.</p>}
    </main>
  )
}
