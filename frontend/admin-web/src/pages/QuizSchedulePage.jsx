import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { useChatSocket } from '../lib/useChatSocket.js'

const today = () => new Date().toLocaleDateString('sv-SE')
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x.toLocaleDateString('sv-SE')
}
const fmt = (s) => s?.replace('T', ' ').slice(0, 16)
const hm = (s) => s?.replace('T', ' ').slice(11, 19)

/**
 * 관리자 > 퀴즈 관리 > 편성 퀴즈 관리
 * 스케줄(방송)마다 출제할 퀴즈를 담아 두고, 채널이 LIVE 일 때 [출제] → 시청자 응답 실시간 집계 → 자동/조기 마감
 */
export default function QuizSchedulePage() {
  const [from, setFrom] = useState(addDays(today(), -1))
  const [schedules, setSchedules] = useState([])
  const [selectedId, setSelectedId] = useState(null)
  const [error, setError] = useState('')
  const to = addDays(from, 7)

  const loadList = () =>
    api.quizSchedules.list(from, to)
      .then((list) => {
        setSchedules(list)
        setSelectedId((id) => id ?? list.find((s) => s.channelStatus === 'LIVE')?.id ?? list[0]?.id ?? null)
      })
      .catch((e) => setError(e.message))

  useEffect(() => { loadList() }, [from])

  return (
    <>
      <h2>편성 퀴즈 관리</h2>
      {error && <p className="error">{error}</p>}
      <div className="split">
        <section className="split-left">
          <div className="toolbar">
            <button className="ghost" onClick={() => setFrom(addDays(from, -7))}>◀</button>
            <strong className="small">{from} ~ {to}</strong>
            <button className="ghost" onClick={() => setFrom(addDays(from, 7))}>▶</button>
          </div>
          <ul className="schedule-list">
            {schedules.map((s) => (
              <li key={s.id} className={s.id === selectedId ? 'active' : ''} onClick={() => setSelectedId(s.id)}>
                <div>
                  {s.channelStatus === 'LIVE' && <span className="badge live">LIVE</span>} <strong>{s.title}</strong>
                </div>
                <div className="muted">{s.channelName} · {fmt(s.startAt)} ~ {s.endAt.slice(11, 16)}</div>
                <div className="muted">퀴즈 {s.quizCount}개</div>
              </li>
            ))}
            {schedules.length === 0 && <li className="muted">이 기간에 스케줄이 없습니다.</li>}
          </ul>
        </section>
        <section className="split-right">
          {selectedId
            ? <ScheduleQuizDetail key={selectedId} scheduleId={selectedId} onChanged={loadList} />
            : <p className="muted">왼쪽에서 스케줄을 선택하세요.</p>}
        </section>
      </div>
    </>
  )
}

function ScheduleQuizDetail({ scheduleId, onChanged }) {
  const [detail, setDetail] = useState(null)
  const [bank, setBank] = useState([])
  const [pick, setPick] = useState('')
  const [live, setLive] = useState(null) // { pushId, counts, total, result? }
  const [error, setError] = useState('')

  const load = () =>
    api.quizSchedules.detail(scheduleId)
      .then((d) => {
        setDetail(d)
        const open = d.pushes.find((p) => p.open)
        setLive((cur) => (open ? { pushId: open.id, counts: open.counts, total: open.total } : cur?.result ? cur : null))
      })
      .catch((e) => setError(e.message))

  useEffect(() => {
    load()
    api.quizzes.list({ size: 100 }).then((r) => setBank(r.items)).catch(() => {})
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleId])

  const channelCode = detail?.schedule.channelCode
  const connected = useChatSocket(channelCode, (m) => {
    const p = m.payload || {}
    if (m.type === 'QUIZ_START') setLive({ pushId: p.pushId, counts: (p.options || []).map(() => 0), total: 0 })
    if (m.type === 'QUIZ_STATS') setLive((cur) => (cur?.pushId === p.pushId ? { ...cur, counts: p.counts, total: p.total } : cur))
    if (m.type === 'QUIZ_RESULT') {
      setLive((cur) => (cur?.pushId === p.pushId ? { ...cur, counts: p.counts, total: p.total, result: true } : cur))
      load()
    }
  })

  const run = async (fn) => {
    setError('')
    try {
      await fn()
      await load()
      onChanged()
    } catch (e) {
      setError(e.message)
    }
  }

  if (!detail) return <p className="muted">불러오는 중...</p>

  const { schedule, quizzes, pushes } = detail
  const isLive = schedule.channelStatus === 'LIVE'
  const openPush = pushes.find((p) => p.open)
  const inSchedule = new Set(quizzes.map((q) => q.quiz.id))
  const move = (i, d) => {
    const ids = quizzes.map((q) => q.mappingId)
    const j = i + d
    if (j < 0 || j >= ids.length) return
    ;[ids[i], ids[j]] = [ids[j], ids[i]]
    run(() => api.quizSchedules.reorder(scheduleId, ids))
  }
  const pushedCount = (quizId) => pushes.filter((p) => p.quizId === quizId).length

  return (
    <>
      <div className="card">
        <div className="form-row">
          <strong>{schedule.title}</strong>
          <span className={`badge ${isLive ? 'live' : ''}`}>{schedule.channelStatus}</span>
          <span className="muted">{schedule.channelName} · {fmt(schedule.startAt)} ~ {fmt(schedule.endAt)}</span>
          <span className={connected ? 'ok small' : 'warn small'}>{connected ? '● 실시간' : '○ 연결 중'}</span>
        </div>
        {!isLive && <p className="muted">채널이 LIVE(OBS 송출 중)일 때 출제할 수 있습니다. 지금은 퀴즈를 미리 담아 두세요.</p>}
      </div>
      {error && <p className="error">{error}</p>}

      {openPush && <LivePanel push={openPush} live={live} onClose={() => run(() => api.quizSchedules.close(openPush.id))} />}

      <h3>출제 목록</h3>
      <div className="form-row card">
        <select value={pick} onChange={(e) => setPick(e.target.value)}>
          <option value="">문제 은행에서 선택</option>
          {bank.filter((q) => !inSchedule.has(q.id)).map((q) => (
            <option key={q.id} value={q.id}>[{q.mode === 'VOTE' ? '투표' : '퀴즈'}] {q.title} — {q.question}</option>
          ))}
        </select>
        <button disabled={!pick} onClick={() => run(async () => { await api.quizSchedules.add(scheduleId, [Number(pick)]); setPick('') })}>추가</button>
      </div>

      <table className="table">
        <thead><tr><th>순서</th><th>유형</th><th>문제</th><th>보기</th><th>제한</th><th></th></tr></thead>
        <tbody>
          {quizzes.map((m, i) => (
            <tr key={m.mappingId}>
              <td className="nowrap">
                {i + 1}
                <button className="ghost" onClick={() => move(i, -1)} disabled={i === 0}>▲</button>
                <button className="ghost" onClick={() => move(i, 1)} disabled={i === quizzes.length - 1}>▼</button>
              </td>
              <td><span className={`badge ${m.quiz.mode === 'VOTE' ? 'vote' : 'quiz'}`}>{m.quiz.mode === 'VOTE' ? '투표' : '퀴즈'}</span></td>
              <td><strong>{m.quiz.title}</strong><div>{m.quiz.question}</div></td>
              <td className="options">
                {m.quiz.options.map((o, k) => <span key={k} className={m.quiz.answerIndex === k ? 'answer' : ''}>{k + 1}. {o}</span>)}
              </td>
              <td>{m.quiz.timeLimitSec}초</td>
              <td className="actions">
                <button disabled={!isLive || !!openPush} onClick={() => run(() => api.quizSchedules.push(scheduleId, m.quiz.id))}>
                  출제{pushedCount(m.quiz.id) ? ` (${pushedCount(m.quiz.id)})` : ''}
                </button>
                <button className="ghost danger" onClick={() => run(() => api.quizSchedules.remove(scheduleId, m.mappingId))}>빼기</button>
              </td>
            </tr>
          ))}
          {quizzes.length === 0 && <tr><td colSpan="6" className="muted center">담긴 퀴즈가 없습니다.</td></tr>}
        </tbody>
      </table>

      <h3>출제 이력</h3>
      <table className="table">
        <thead><tr><th>출제</th><th>마감</th><th>문제</th><th>결과</th><th>참여</th></tr></thead>
        <tbody>
          {pushes.map((p) => (
            <tr key={p.id}>
              <td>{hm(p.pushedAt)}</td>
              <td>{p.open ? <span className="badge live">진행 중</span> : hm(p.closesAt)}</td>
              <td>[{p.mode === 'VOTE' ? '투표' : '퀴즈'}] {p.question}</td>
              <td><Bars options={p.options} counts={p.counts} total={p.total} answerIndex={p.answerIndex} /></td>
              <td>
                {p.total}명
                {p.answerIndex != null && p.total > 0 && <div className="muted">정답률 {Math.round(((p.counts[p.answerIndex] || 0) * 100) / p.total)}%</div>}
              </td>
            </tr>
          ))}
          {pushes.length === 0 && <tr><td colSpan="5" className="muted center">출제 이력이 없습니다.</td></tr>}
        </tbody>
      </table>
    </>
  )
}

function LivePanel({ push, live, onClose }) {
  const [now, setNow] = useState(Date.now())
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 500)
    return () => clearInterval(t)
  }, [])
  const remain = Math.max(0, Math.ceil((new Date(push.closesAt).getTime() - now) / 1000))
  const counts = live?.pushId === push.id ? live.counts : push.counts
  const total = live?.pushId === push.id ? live.total : push.total
  return (
    <div className="card live-panel">
      <div className="form-row">
        <span className="badge live">진행 중</span>
        <strong>{push.question}</strong>
        <span className="timer">{remain}초</span>
        <span className="spacer" />
        <button className="ghost danger" onClick={onClose}>지금 마감</button>
      </div>
      <Bars options={push.options} counts={counts} total={total} answerIndex={push.answerIndex} />
      <p className="muted">참여 {total}명 (1초마다 갱신)</p>
    </div>
  )
}

function Bars({ options, counts = [], total, answerIndex }) {
  return (
    <div className="bars">
      {options.map((o, i) => {
        const c = counts[i] || 0
        const pct = total > 0 ? Math.round((c * 100) / total) : 0
        return (
          <div key={i} className={`bar-row ${answerIndex === i ? 'answer' : ''}`}>
            <span className="bar-label">{i + 1}. {o}</span>
            <span className="bar"><span style={{ width: `${pct}%` }} /></span>
            <span className="bar-num">{pct}% ({c})</span>
          </div>
        )
      })}
    </div>
  )
}
