import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { useChatSocket, VIEWER_URL } from '../lib/useChatSocket.js'
import OmePlayer from '../components/OmePlayer.jsx'
import { LiveFeed } from './ChatMessagePage.jsx'
import { Bars } from './QuizSchedulePage.jsx'

const POLL_MS = 3000
const REC_LABEL = { NONE: '녹화 안 함', RECORDING: '● 녹화 중', READY: '녹화 완료', FAILED: '녹화 실패' }

const elapsed = (from, now) => {
  if (!from) return '-'
  const sec = Math.max(0, Math.floor((now - new Date(from).getTime()) / 1000))
  const h = Math.floor(sec / 3600)
  const m = String(Math.floor((sec % 3600) / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  return `${h}:${m}:${s}`
}
const hm = (s) => s?.slice(11, 16)

/**
 * 관리자 > LIVE > 모니터링
 * 위: 전체 채널 상태 카드 / 아래: 선택한 방송 통제실 (미리보기·방송 중지·공지 | 퀴즈 출제·마감 | 채팅 얼리기·메시지 관리)
 */
export default function MonitoringPage() {
  const [channels, setChannels] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [error, setError] = useState('')
  const now = useNow()

  const load = () =>
    api.monitor.channels()
      .then((list) => {
        setChannels(list)
        setSelectedId((id) => id ?? list.find((c) => c.status === 'LIVE')?.id ?? list[0]?.id ?? null)
        setError('')
      })
      .catch((e) => setError(e.message))

  useEffect(() => {
    load()
    const t = setInterval(load, POLL_MS)
    return () => clearInterval(t)
  }, [])

  const liveCount = channels.filter((c) => c.status === 'LIVE').length
  const totalViewers = channels.reduce((a, c) => a + c.viewerCount, 0)

  return (
    <>
      <div className="mon-title">
        <h2>모니터링</h2>
        <span className="muted">방송 중 {liveCount}개 · 전체 시청자 {totalViewers.toLocaleString()}명</span>
      </div>
      {error && <p className="error">{error}</p>}

      <div className="mon-cards">
        {channels.map((c) => (
          <button key={c.id} className={`mon-card ${c.id === selectedId ? 'active' : ''} ${c.status === 'LIVE' ? 'live' : ''}`}
            onClick={() => setSelectedId(c.id)}>
            <div className="mon-card-top">
              <span className={`badge ${c.status === 'LIVE' ? 'live' : ''}`}>{c.status}</span>
              {c.publishLocked && <span className="badge st-DELETED">송출 잠금</span>}
              {c.chatFrozen && <span className="badge ice">❄ 채팅</span>}
              {c.quiz && <span className="badge vote">{c.quiz.mode === 'VOTE' ? '투표 중' : '퀴즈 중'}</span>}
            </div>
            <strong>{c.name}</strong>
            <div className="muted small">{c.schedule ? c.schedule.title : '편성 없음'}</div>
            {c.status === 'LIVE' && (
              <div className="mon-card-stats">
                <span>👁 {c.viewerCount.toLocaleString()}</span>
                <span>⏱ {elapsed(c.liveStartedAt, now)}</span>
                {c.stream && <span>{c.stream.kbpsIn.toLocaleString()} kbps</span>}
              </div>
            )}
          </button>
        ))}
        {channels.length === 0 && <p className="muted">채널이 없습니다.</p>}
      </div>

      {selectedId && <ControlRoom key={selectedId} channelId={selectedId} onChanged={load} now={now} />}
    </>
  )
}

// ─────────────────────────────────────────────
// 통제실
// ─────────────────────────────────────────────

function ControlRoom({ channelId, onChanged, now }) {
  const [detail, setDetail] = useState(null)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  const load = () => api.monitor.detail(channelId).then(setDetail).catch((e) => setError(e.message))

  useEffect(() => {
    load()
    const t = setInterval(load, POLL_MS)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelId])

  const run = async (fn, okMessage) => {
    setError('')
    setInfo('')
    try {
      const r = await fn()
      if (okMessage) setInfo(typeof okMessage === 'function' ? okMessage(r) : okMessage)
      await load()
      onChanged()
    } catch (e) {
      setError(e.message)
    }
  }

  if (!detail) return <p className="muted">불러오는 중...</p>
  const c = detail.channel

  return (
    <section className="control">
      <div className="control-head">
        <h3>{c.name} <span className="muted small">({c.code})</span></h3>
        {error && <span className="error">{error}</span>}
        {info && <span className="ok">{info}</span>}
      </div>

      <div className="control-grid">
        <BroadcastPanel c={c} now={now} run={run} />
        <QuizPanel c={c} scheduleQuizzes={detail.scheduleQuizzes} run={run} reload={load} />
        <ChatPanel c={c} run={run} reload={load} />
      </div>
    </section>
  )
}

/** 1열: 방송 미리보기 + 상태 + 방송 중지/허용 + 공지 */
function BroadcastPanel({ c, now, run }) {
  const [notice, setNotice] = useState('')
  const [protocol, setProtocol] = useState(null)
  const live = c.status === 'LIVE'
  const s = c.stream
  const overlayUrl = `${VIEWER_URL}/overlay/${c.code}/quiz`

  const stop = () => {
    const msg = prompt('방송을 중지합니다. 시청자에게 보낼 안내 문구 (비워두면 기본 문구)\n※ 송출이 잠겨 OBS 가 다시 연결해도 거부됩니다.', '')
    if (msg === null) return
    run(() => api.monitor.stop(c.id, msg), (r) => (r.kicked ? '방송을 중지했습니다.' : '송출을 잠갔습니다. (OME 연결 실패 — OBS 에서도 송출을 종료해 주세요)'))
  }

  return (
    <div className="panel">
      <div className="panel-title">방송</div>
      {live ? (
        <div className="preview-wrap">
          <OmePlayer webrtcUrl={c.webrtcUrl} hlsUrl={c.playbackUrl} onProtocol={setProtocol} />
          <span className="preview-mode">{protocol ?? '연결 중'}</span>
        </div>
      ) : <div className="preview off">{c.publishLocked ? '송출 잠김 (방송 중지됨)' : '방송 중이 아닙니다'}</div>}

      <dl className="kv">
        <dt>상태</dt><dd><span className={`badge ${live ? 'live' : ''}`}>{c.status}</span>{c.publishLocked && <span className="badge st-DELETED">송출 잠금</span>}</dd>
        <dt>시청자</dt><dd>{c.viewerCount.toLocaleString()}명</dd>
        <dt>방송 시간</dt><dd>{live ? elapsed(c.liveStartedAt, now) : '-'}</dd>
        <dt>송출 품질</dt>
        <dd>
          {s ? `${s.kbpsIn.toLocaleString()} kbps · ${s.width ?? '?'}×${s.height ?? '?'}${s.framerate ? ` ${Math.round(s.framerate)}fps` : ''} · ${s.videoCodec ?? ''}/${s.audioCodec ?? ''}` : '-'}
          {s?.bframes && <div className="error small">⚠ B-프레임 감지 — WebRTC 화면이 끊길 수 있습니다 (OBS 프로파일 baseline / B-프레임 0)</div>}
        </dd>
        <dt>미디어 접속</dt><dd>{s ? `${s.mediaConnections.toLocaleString()}개 (WebRTC·LL-HLS)` : '-'}</dd>
        <dt>편성</dt><dd>{c.schedule ? `${c.schedule.title} (${hm(c.schedule.startAt)}~${hm(c.schedule.endAt)})` : '없음'}</dd>
        <dt>녹화</dt><dd>{c.broadcast ? REC_LABEL[c.broadcast.recordingStatus] : '-'}</dd>
      </dl>

      <div className="btn-row">
        {c.publishLocked
          ? <button onClick={() => run(() => api.monitor.allowPublish(c.id), '송출을 허용했습니다. OBS 에서 방송을 시작하세요.')}>송출 허용</button>
          : <button className="danger-fill" onClick={stop}>■ 방송 중지</button>}
        <a className="ghost-link" href={`${VIEWER_URL}/watch/${c.code}`} target="_blank" rel="noreferrer">시청 화면 ↗</a>
      </div>

      <form className="inline-form" onSubmit={(e) => {
        e.preventDefault()
        if (!notice.trim()) return
        run(() => api.monitor.notice(c.id, notice), '공지를 보냈습니다.').then(() => setNotice(''))
      }}>
        <input value={notice} onChange={(e) => setNotice(e.target.value)} maxLength={200} placeholder="📢 공지 (채팅창 시스템 메시지)" />
        <button type="submit">공지</button>
      </form>

      <div className="muted small overlay-line" title="클릭하여 복사" onClick={() => navigator.clipboard?.writeText(overlayUrl)}>
        OBS 퀴즈 오버레이: <code className="mono">{overlayUrl}</code>
      </div>
    </div>
  )
}

/** 2열: 진행 중 퀴즈 + 편성 퀴즈 출제 + 문제 은행 바로 출제 */
function QuizPanel({ c, scheduleQuizzes, run, reload }) {
  const [bank, setBank] = useState([])
  const [pick, setPick] = useState('')
  const [live, setLive] = useState(null)
  const [now, setNow] = useState(Date.now())
  const isLive = c.status === 'LIVE'
  const quiz = c.quiz

  useEffect(() => { api.quizzes.list({ size: 100 }).then((r) => setBank(r.items)).catch(() => {}) }, [])
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(t)
  }, [])

  // 실시간 집계 (폴링보다 빠르게)
  useChatSocket(c.code, (m) => {
    const p = m.payload || {}
    if (m.type === 'QUIZ_STATS') setLive({ pushId: p.pushId, counts: p.counts, total: p.total })
    if (m.type === 'QUIZ_START' || m.type === 'QUIZ_RESULT') reload()
  })

  const push = (quizId) => run(() => api.monitor.pushQuiz(c.id, quizId), '출제했습니다.')
  const counts = live?.pushId === quiz?.pushId ? live.counts : quiz?.counts
  const total = live?.pushId === quiz?.pushId ? live.total : quiz?.total
  const remain = quiz ? Math.max(0, Math.ceil((new Date(quiz.closesAt).getTime() - now) / 1000)) : 0
  const disabled = !isLive || !!quiz

  return (
    <div className="panel">
      <div className="panel-title">퀴즈 / 투표</div>

      {quiz ? (
        <div className="live-panel card">
          <div className="form-row">
            <span className="badge live">{quiz.mode === 'VOTE' ? '투표 중' : '퀴즈 중'}</span>
            <strong className="grow">{quiz.question}</strong>
            <span className="timer">{remain}초</span>
          </div>
          <Bars options={quiz.options} counts={counts} total={total} answerIndex={quiz.answerIndex} />
          <div className="form-row">
            <span className="muted">참여 {total ?? 0}명</span>
            <span className="spacer" />
            <button className="ghost danger" onClick={() => run(() => api.quizSchedules.close(quiz.pushId), '마감했습니다.')}>지금 마감</button>
          </div>
        </div>
      ) : (
        <p className="muted small">{isLive ? '진행 중인 퀴즈가 없습니다.' : '방송 중(LIVE)일 때 출제할 수 있습니다.'}</p>
      )}

      <div className="sub-title">이 방송 편성 퀴즈 {c.schedule ? '' : '(편성 없음)'}</div>
      <ul className="quiz-list">
        {scheduleQuizzes.map((m) => (
          <li key={m.mappingId}>
            <span className={`badge ${m.quiz.mode === 'VOTE' ? 'vote' : 'quiz'}`}>{m.quiz.mode === 'VOTE' ? '투표' : '퀴즈'}</span>
            <span className="grow" title={m.quiz.options.join(' / ')}>{m.quiz.question}</span>
            <button disabled={disabled} onClick={() => push(m.quiz.id)}>출제</button>
          </li>
        ))}
        {scheduleQuizzes.length === 0 && <li className="muted small">편성 퀴즈 관리에서 이 방송에 퀴즈를 담아 두면 여기서 바로 출제할 수 있습니다.</li>}
      </ul>

      <div className="sub-title">문제 은행에서 바로 출제</div>
      <div className="inline-form">
        <select value={pick} onChange={(e) => setPick(e.target.value)}>
          <option value="">선택</option>
          {bank.map((q) => <option key={q.id} value={q.id}>[{q.mode === 'VOTE' ? '투표' : '퀴즈'}] {q.question}</option>)}
        </select>
        <button disabled={disabled || !pick} onClick={() => push(Number(pick)).then(() => setPick(''))}>출제</button>
      </div>
    </div>
  )
}

/** 3열: 채팅 얼리기 + 실시간 채팅 관리 */
function ChatPanel({ c, run, reload }) {
  const freeze = (data, msg) => run(() => api.chat.freeze(c.id, data), msg)
  return (
    <div className="panel monitor-chat">
      <div className="panel-title">
        채팅
        <span className="spacer" />
        <button className={`toggle ${c.chatFrozen ? 'on' : ''}`} onClick={() => freeze({ chatFrozen: !c.chatFrozen }, c.chatFrozen ? '채팅 얼리기 해제' : '채팅을 얼렸습니다.')}>
          {c.chatFrozen ? '❄ 얼림 (해제)' : '채팅 얼리기'}
        </button>
        <button className={`toggle ${c.stickerFrozen ? 'on' : ''}`} onClick={() => freeze({ stickerFrozen: !c.stickerFrozen }, c.stickerFrozen ? '스티커 허용' : '스티커를 막았습니다.')}>
          {c.stickerFrozen ? '스티커 중지 (해제)' : '스티커 얼리기'}
        </button>
      </div>
      <LiveFeed channelCode={c.code} onFreezeChanged={reload} />
    </div>
  )
}

function useNow() {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(t)
  }, [])
  return now
}
