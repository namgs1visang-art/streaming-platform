import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import HlsPlayer from '../components/HlsPlayer.jsx'
import ChatPanel from '../components/ChatPanel.jsx'

export default function WatchPage() {
  const { code } = useParams()
  const [channel, setChannel] = useState(null)

  useEffect(() => {
    const load = () => fetch(`/api/channels/${code}`).then((r) => r.json()).then(setChannel).catch(() => {})
    load()
    const t = setInterval(load, 10000)
    return () => clearInterval(t)
  }, [code])

  if (!channel) return <main className="container muted">불러오는 중...</main>

  const live = channel.status === 'LIVE'

  return (
    <main className="watch">
      <section className="player-area">
        {live ? (
          <HlsPlayer src={channel.playbackUrl} />
        ) : (
          <div className="offline">지금은 방송 중이 아닙니다.</div>
        )}
        <div className="info">
          <h3>{channel.name} {live && <span className="live">LIVE</span>}</h3>
          <p className="muted">{channel.description}{live && ` · 시청자 ${channel.viewerCount}명`}</p>
        </div>
      </section>
      <ChatPanel channelCode={code} />
    </main>
  )
}
