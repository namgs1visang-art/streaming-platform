import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'

export default function HomePage() {
  const [channels, setChannels] = useState([])

  useEffect(() => {
    const load = () => fetch('/api/channels').then((r) => r.json()).then(setChannels).catch(() => {})
    load()
    const t = setInterval(load, 10000)
    return () => clearInterval(t)
  }, [])

  return (
    <main className="container">
      <h2>채널</h2>
      <div className="grid">
        {channels.map((c) => (
          <Link key={c.id} to={`/watch/${c.code}`} className={`tile ${c.status === 'LIVE' ? '' : 'off'}`}>
            <div className="thumb">
              {c.status === 'LIVE' ? <span className="live">LIVE</span> : <span>OFFLINE</span>}
              {c.status === 'LIVE' && <span className="viewers">👁 {c.viewerCount}</span>}
            </div>
            <div className="tile-body">
              <strong>{c.name}</strong>
              <p>{c.description}</p>
            </div>
          </Link>
        ))}
      </div>
      {channels.length === 0 && <p className="muted">채널이 없습니다. 관리자 화면에서 채널을 만들어 주세요.</p>}
    </main>
  )
}
