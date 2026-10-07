import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { fmtDuration, http } from '../lib/http.js'

const fmt = (s) => s?.replace('T', ' ').slice(0, 16)

/** 다시보기 목록 (녹화가 끝난 방송) */
export default function ReplayListPage() {
  const [items, setItems] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    http('GET', '/api/replays?size=48')
      .then((r) => setItems(r.items))
      .catch((e) => setError(e.message))
  }, [])

  return (
    <main className="container">
      <h2>다시보기</h2>
      {error && <p className="muted">{error}</p>}
      <div className="grid">
        {(items || []).map((r) => (
          <Link key={r.id} to={`/replay/${r.id}`} className="tile">
            <div className="thumb">
              <span className="replay-badge">다시보기</span>
              <span className="viewers">{fmtDuration(r.durationSec)}</span>
            </div>
            <div className="tile-body">
              <strong>{r.title}</strong>
              <p>{r.channelName} · {fmt(r.startedAt)}</p>
            </div>
          </Link>
        ))}
      </div>
      {items?.length === 0 && <p className="muted">아직 다시보기가 없습니다. 녹화가 켜진 방송이 끝나면 여기에 표시됩니다.</p>}
    </main>
  )
}
