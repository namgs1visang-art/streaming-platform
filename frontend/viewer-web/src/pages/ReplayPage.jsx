import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fmtDuration, http } from '../lib/http.js'
import ChatLine from '../components/ChatLine.jsx'
import QuizCard from '../components/QuizCard.jsx'

const CHUNK_SEC = 120          // 채팅은 120초 단위로 가져온다 (현재 구간 + 앞뒤 구간)
const RESULT_LINGER_SEC = 8    // 퀴즈 마감 후 결과를 보여주는 시간
const VISIBLE_MAX = 150

/**
 * 다시보기: 녹화 영상 + 그 방송 시간대의 채팅/퀴즈를 영상 시간에 맞춰 재생.
 * 영상 0초 = 방송 시작, 채팅 offsetMs 가 현재 재생 위치보다 앞선 것만 보여준다.
 */
export default function ReplayPage() {
  const { id } = useParams()
  const [data, setData] = useState(null)
  const [error, setError] = useState('')
  const [t, setT] = useState(0)
  const [version, setVersion] = useState(0)
  const [dismissed, setDismissed] = useState(() => new Set())
  const videoRef = useRef(null)
  const listRef = useRef(null)
  const chunks = useRef(new Map())
  const pending = useRef(new Set())

  useEffect(() => {
    chunks.current = new Map()
    http('GET', `/api/replays/${id}`).then(setData).catch((e) => setError(e.message))
  }, [id])

  const ensureChunk = useCallback(
    async (idx) => {
      if (idx < 0 || chunks.current.has(idx) || pending.current.has(idx)) return
      pending.current.add(idx)
      try {
        const r = await http('GET', `/api/replays/${id}/chat?from=${idx * CHUNK_SEC}&to=${(idx + 1) * CHUNK_SEC}`)
        chunks.current.set(idx, r.messages)
        setVersion((v) => v + 1)
      } catch { /* 다음 시간 갱신 때 재시도 */ } finally {
        pending.current.delete(idx)
      }
    },
    [id],
  )

  const bucket = Math.floor(t / 30)
  useEffect(() => {
    if (!data) return
    const idx = Math.floor(t / CHUNK_SEC)
    ;[idx - 1, idx, idx + 1].forEach((i) => void ensureChunk(i))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [data, bucket, ensureChunk])

  const visible = useMemo(() => {
    const idx = Math.floor(t / CHUNK_SEC)
    const pool = [...(chunks.current.get(idx - 1) ?? []), ...(chunks.current.get(idx) ?? [])]
    return pool.filter((m) => m.offsetMs / 1000 <= t).slice(-VISIBLE_MAX)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [t, version])

  useEffect(() => {
    listRef.current?.scrollTo(0, listRef.current.scrollHeight)
  }, [visible.length])

  const seek = (sec) => {
    const v = videoRef.current
    if (!v) return
    v.currentTime = Math.max(0, sec)
    v.play().catch(() => {})
  }

  if (error) return <main className="container"><h3>다시보기를 볼 수 없습니다</h3><p className="muted">{error}</p></main>
  if (!data) return <main className="container muted">불러오는 중...</main>

  const active = data.quizzes.find(
    (q) => t >= q.offsetSec && t <= q.offsetSec + q.durationSec + RESULT_LINGER_SEC && !dismissed.has(q.pushId),
  )
  const remain = active ? Math.max(0, Math.ceil(active.offsetSec + active.durationSec - t)) : 0

  return (
    <main className="watch">
      <section className="player-area">
        <video
          ref={videoRef}
          className="video"
          src={data.videoUrl}
          controls
          playsInline
          onTimeUpdate={(e) => setT(e.currentTarget.currentTime)}
          onSeeked={(e) => setT(e.currentTarget.currentTime)}
        />
        <div className="info">
          <h3>{data.title} <span className="replay-badge">다시보기</span></h3>
          <p className="muted">{data.channelName} · {fmtDuration(data.durationSec)} · 채팅 {data.chatCount}건</p>
        </div>
        {data.quizzes.length > 0 && (
          <div className="quiz-timeline">
            <strong>퀴즈/투표</strong>
            {data.quizzes.map((q) => (
              <button key={q.pushId} type="button" className="ghost-btn" onClick={() => {
                setDismissed((d) => { const n = new Set(d); n.delete(q.pushId); return n })
                seek(q.offsetSec)
              }}>
                {fmtDuration(q.offsetSec)} · {q.mode === 'VOTE' ? '투표' : '퀴즈'} · {q.title}
              </button>
            ))}
          </div>
        )}
      </section>

      <aside className="chat">
        <div className="chat-head">채팅 다시보기</div>
        {active && (
          <QuizCard
            quiz={active}
            stats={null}
            result={remain === 0 ? { counts: active.counts, total: active.total, answerIndex: active.answerIndex } : null}
            remainSec={remain}
            onClose={() => setDismissed((d) => new Set(d).add(active.pushId))}
          />
        )}
        <ul className="chat-list" ref={listRef}>
          {visible.map((m) => <ChatLine key={m.id} m={m} />)}
          {visible.length === 0 && <li className="muted">이 시점까지 채팅이 없습니다.</li>}
        </ul>
      </aside>
    </main>
  )
}
