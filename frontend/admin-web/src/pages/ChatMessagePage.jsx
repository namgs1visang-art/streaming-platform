import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api.js'
import { useChatSocket, VIEWER_URL } from '../lib/useChatSocket.js'

const MAX_FEED = 500
const today = () => new Date().toLocaleDateString('sv-SE')
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x.toLocaleDateString('sv-SE')
}
const time = (iso) => (iso ? new Date(iso).toLocaleTimeString('ko-KR', { hour12: false }) : '')
const dateTime = (iso) => (iso ? new Date(iso).toLocaleString('sv-SE').slice(0, 19) : '')
const STATUS_LABEL = { VISIBLE: '노출', HIDDEN: '숨김', DELETED: '삭제' }

/** 관리자 > 채팅 관리 > 채팅 메시지 관리 */
export default function ChatMessagePage() {
  const [rooms, setRooms] = useState([])
  const [code, setCode] = useState('')
  const [tab, setTab] = useState('live')
  const [error, setError] = useState('')

  const loadRooms = () =>
    api.chat.rooms()
      .then((list) => {
        setRooms(list)
        setCode((c) => c || list[0]?.channelCode || '')
      })
      .catch((e) => setError(e.message))

  useEffect(() => { loadRooms() }, [])

  const room = rooms.find((r) => r.channelCode === code)

  const freeze = async (data) => {
    if (!room) return
    setError('')
    try {
      const updated = await api.chat.freeze(room.channelId, data)
      setRooms((list) => list.map((r) => (r.channelId === updated.channelId ? updated : r)))
    } catch (e) {
      setError(e.message)
    }
  }

  return (
    <>
      <h2>채팅 메시지 관리</h2>

      <div className="card form-row">
        <span className="label">채팅방</span>
        <select value={code} onChange={(e) => setCode(e.target.value)}>
          {rooms.map((r) => (
            <option key={r.channelId} value={r.channelCode}>[{r.status}] {r.name} ({r.channelCode})</option>
          ))}
        </select>
        {room && (
          <>
            <button className={`toggle ${room.chatFrozen ? 'on' : ''}`} onClick={() => freeze({ chatFrozen: !room.chatFrozen })}>
              {room.chatFrozen ? '❄ 채팅 얼림 (해제)' : '채팅 얼리기'}
            </button>
            <button className={`toggle ${room.stickerFrozen ? 'on' : ''}`} onClick={() => freeze({ stickerFrozen: !room.stickerFrozen })}>
              {room.stickerFrozen ? '스티커 중지됨 (해제)' : '스티커 얼리기'}
            </button>
            <a className="link" href={`${VIEWER_URL}/watch/${room.channelCode}`} target="_blank" rel="noreferrer">시청 화면 열기 ↗</a>
          </>
        )}
      </div>
      {error && <p className="error">{error}</p>}

      <div className="tabs">
        <button className={tab === 'live' ? 'active' : ''} onClick={() => setTab('live')}>실시간 채팅</button>
        <button className={tab === 'history' ? 'active' : ''} onClick={() => setTab('history')}>메시지 이력</button>
      </div>

      {!code && <p className="muted">채널이 없습니다. 채널 관리에서 먼저 채널을 만들어 주세요.</p>}
      {code && tab === 'live' && (
        <LiveFeed key={code} channelCode={code} onFreezeChanged={loadRooms} />
      )}
      {code && tab === 'history' && <History key={code} channelCode={code} />}
    </>
  )
}

// ─────────────────────────────────────────────
// 실시간 피드
// ─────────────────────────────────────────────

function LiveFeed({ channelCode, onFreezeChanged }) {
  const [feed, setFeed] = useState([])
  const [keyword, setKeyword] = useState('')
  const [showModerated, setShowModerated] = useState(true)
  const [draft, setDraft] = useState('')
  const [autoScroll, setAutoScroll] = useState(true)
  const [error, setError] = useState('')
  const listRef = useRef(null)

  const add = (items) =>
    setFeed((f) => {
      const seen = new Set(f.map((x) => x.id))
      const fresh = items.filter((x) => !seen.has(x.id))
      return fresh.length ? [...f, ...fresh].slice(-MAX_FEED) : f
    })
  const setStatus = (ids, status) => setFeed((f) => f.map((x) => (ids.includes(x.id) ? { ...x, status } : x)))
  const sys = (text, sentAt) => add([{ id: `sys-${Date.now()}-${Math.random()}`, type: 'SYSTEM', content: text, sentAt, status: 'VISIBLE' }])

  useEffect(() => {
    api.chat.recent(channelCode)
      .then((rows) =>
        setFeed((prev) => {
          const ids = new Set(rows.map((m) => m.id))
          return [...rows, ...prev.filter((m) => !ids.has(m.id))].slice(-MAX_FEED)
        }),
      )
      .catch((e) => setError(e.message))
  }, [channelCode])

  const connected = useChatSocket(channelCode, (m) => {
    const p = m.payload || {}
    switch (m.type) {
      case 'CHAT':
      case 'STICKER':
      case 'ADMIN':
        add([{ ...m, status: 'VISIBLE' }])
        break
      case 'SYSTEM':
        sys(m.content, m.sentAt)
        break
      case 'CHAT_FREEZE':
        sys(m.content, m.sentAt)
        onFreezeChanged()
        break
      case 'MESSAGE_DELETE':
        setStatus(p.ids || [], 'DELETED')
        break
      case 'MESSAGE_HIDE':
        setStatus(p.ids || [], 'HIDDEN')
        break
      case 'MESSAGE_UNHIDE':
        if (p.message) setStatus([p.message.id], 'VISIBLE')
        break
      case 'QUIZ_START':
        sys(`${p.mode === 'VOTE' ? '투표' : '퀴즈'} 출제: ${m.content}`, m.sentAt)
        break
      case 'QUIZ_RESULT':
        sys(`퀴즈 마감 · ${p.total}명 참여${p.answerIndex != null ? ` · 정답 ${p.answerIndex + 1}번` : ''}`, m.sentAt)
        break
      default:
    }
  })

  useEffect(() => {
    if (autoScroll) listRef.current?.scrollTo(0, listRef.current.scrollHeight)
  }, [feed, autoScroll])

  const visible = useMemo(
    () =>
      feed.filter((m) => {
        if (!showModerated && m.status !== 'VISIBLE') return false
        if (keyword && !`${m.sender ?? ''} ${m.content ?? ''}`.toLowerCase().includes(keyword.toLowerCase())) return false
        return true
      }),
    [feed, keyword, showModerated],
  )

  const act = async (fn, id) => {
    setError('')
    try {
      const updated = await fn(id)
      setStatus([updated.id], updated.status)
    } catch (e) {
      setError(e.message)
    }
  }

  const send = async (e) => {
    e.preventDefault()
    if (!draft.trim()) return
    setError('')
    try {
      await api.chat.send(channelCode, draft)
      setDraft('')
    } catch (err) {
      setError(err.message)
    }
  }

  const onScroll = () => {
    const el = listRef.current
    if (el) setAutoScroll(el.scrollHeight - el.scrollTop - el.clientHeight < 40)
  }

  return (
    <div className="feed-wrap">
      <div className="toolbar">
        <span className={connected ? 'ok' : 'warn'}>{connected ? '● 실시간 수신 중' : '○ 연결 중…'}</span>
        <input className="search" placeholder="닉네임/내용 필터" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <label className="check">
          <input type="checkbox" checked={showModerated} onChange={(e) => setShowModerated(e.target.checked)} /> 숨김/삭제 포함
        </label>
        {!autoScroll && <button className="ghost" onClick={() => setAutoScroll(true)}>↓ 최신으로</button>}
      </div>
      {error && <p className="error">{error}</p>}

      <div className="feed" ref={listRef} onScroll={onScroll}>
        {visible.map((m) => <FeedRow key={m.id} m={m} act={act} />)}
        {visible.length === 0 && <p className="muted center">메시지가 없습니다.</p>}
      </div>

      <form className="admin-send" onSubmit={send}>
        <span className="admin-tag">관리자</span>
        <input value={draft} onChange={(e) => setDraft(e.target.value)} maxLength={200} placeholder="관리자 메시지를 채팅창에 보냅니다" />
        <button type="submit">전송</button>
      </form>
    </div>
  )
}

function FeedRow({ m, act }) {
  if (m.type === 'SYSTEM') {
    return <div className="feed-row system"><span className="t">{time(m.sentAt)}</span> 📢 {m.content}</div>
  }
  return (
    <div className={`feed-row ${m.status !== 'VISIBLE' ? 'moderated' : ''} ${m.type === 'ADMIN' ? 'admin' : ''}`}>
      <span className="t">{time(m.sentAt)}</span>
      <b className="nick" title={m.clientId || ''}>{m.sender}</b>
      <span className="body">
        {m.type === 'STICKER' ? <StickerInline s={m.payload} /> : m.content}
      </span>
      {m.status !== 'VISIBLE' && <span className={`badge st-${m.status}`}>{STATUS_LABEL[m.status]}</span>}
      <span className="row-actions">
        {m.status === 'VISIBLE' && <button className="ghost" onClick={() => act(api.chat.hide, m.id)}>숨김</button>}
        {m.status === 'HIDDEN' && <button className="ghost" onClick={() => act(api.chat.unhide, m.id)}>숨김 해제</button>}
        {m.status !== 'DELETED' && (
          <button className="ghost danger" onClick={() => confirm('메시지를 삭제할까요? (복구 불가)') && act(api.chat.remove, m.id)}>삭제</button>
        )}
      </span>
    </div>
  )
}

function StickerInline({ s }) {
  if (!s) return '[스티커]'
  return s.imageUrl
    ? <img src={s.imageUrl} alt={s.name} className="sticker-sm" />
    : <span className="sticker-emoji" title={s.name}>{s.emoji}</span>
}

// ─────────────────────────────────────────────
// 이력 검색
// ─────────────────────────────────────────────

function History({ channelCode }) {
  const [q, setQ] = useState({ from: addDays(today(), -6), to: today(), keyword: '', status: '' })
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ items: [], total: 0, size: 50 })
  const [error, setError] = useState('')

  const load = (p = page) =>
    api.chat.search({ channelCode, ...q, page: p, size: 50 })
      .then(setData)
      .catch((e) => setError(e.message))

  useEffect(() => { load(0); setPage(0) }, [channelCode])

  const search = (e) => {
    e.preventDefault()
    setError('')
    setPage(0)
    load(0)
  }

  const act = async (fn, id) => {
    setError('')
    try {
      await fn(id)
      load()
    } catch (e) {
      setError(e.message)
    }
  }

  const pages = Math.max(1, Math.ceil(data.total / (data.size || 50)))

  return (
    <>
      <form className="card form-row" onSubmit={search}>
        <input type="date" value={q.from} onChange={(e) => setQ({ ...q, from: e.target.value })} />
        <span>~</span>
        <input type="date" value={q.to} onChange={(e) => setQ({ ...q, to: e.target.value })} />
        <select value={q.status} onChange={(e) => setQ({ ...q, status: e.target.value })}>
          <option value="">전체 상태</option>
          <option value="VISIBLE">노출</option>
          <option value="HIDDEN">숨김</option>
          <option value="DELETED">삭제</option>
        </select>
        <input placeholder="닉네임/내용" value={q.keyword} onChange={(e) => setQ({ ...q, keyword: e.target.value })} />
        <button type="submit">검색</button>
        <span className="muted">총 {data.total.toLocaleString()}건</span>
      </form>
      {error && <p className="error">{error}</p>}

      <table className="table">
        <thead>
          <tr><th>시각</th><th>유형</th><th>닉네임</th><th>내용</th><th>상태</th><th>처리</th><th></th></tr>
        </thead>
        <tbody>
          {data.items.map((m) => (
            <tr key={m.id} className={m.status !== 'VISIBLE' ? 'moderated' : ''}>
              <td className="nowrap">{dateTime(m.sentAt)}</td>
              <td>{m.type}</td>
              <td title={m.clientId || ''}>{m.sender}</td>
              <td>{m.type === 'STICKER' ? <StickerInline s={m.payload} /> : m.content}</td>
              <td><span className={`badge st-${m.status}`}>{STATUS_LABEL[m.status]}</span></td>
              <td className="muted">{m.moderatedBy ? `${m.moderatedBy} ${m.moderatedAt?.replace('T', ' ').slice(5, 16)}` : ''}</td>
              <td className="actions">
                {m.status === 'VISIBLE' && <button className="ghost" onClick={() => act(api.chat.hide, m.id)}>숨김</button>}
                {m.status === 'HIDDEN' && <button className="ghost" onClick={() => act(api.chat.unhide, m.id)}>해제</button>}
                {m.status !== 'DELETED' && <button className="ghost danger" onClick={() => confirm('삭제할까요? (복구 불가)') && act(api.chat.remove, m.id)}>삭제</button>}
              </td>
            </tr>
          ))}
          {data.items.length === 0 && <tr><td colSpan="7" className="muted center">검색 결과가 없습니다.</td></tr>}
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
