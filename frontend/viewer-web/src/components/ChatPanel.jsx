import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'
import { getViewer } from '../lib/identity.js'
import { http } from '../lib/http.js'
import ChatLine, { StickerView } from './ChatLine.jsx'
import QuizCard from './QuizCard.jsx'

const WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8093/ws'
const MAX_MESSAGES = 200
const RESULT_LINGER_MS = 15000 // 퀴즈 결과를 보여주는 시간

/**
 * 채팅 + 스티커 + 퀴즈/투표.
 * 구독: /topic/chat/{channelCode} (채널 전체 이벤트), /user/queue/errors (내 전송 거절 사유)
 * 전송: /app/chat/{channelCode}  { sender, clientId, content } 또는 { sender, clientId, stickerId }
 */
export default function ChatPanel({ channelCode }) {
  const [viewer] = useState(getViewer)
  const [messages, setMessages] = useState([])
  const [frozen, setFrozen] = useState({ chatFrozen: false, stickerFrozen: false })
  const [text, setText] = useState('')
  const [connected, setConnected] = useState(false)
  const [stickers, setStickers] = useState([])
  const [pickerOpen, setPickerOpen] = useState(false)

  // 퀴즈
  const [quiz, setQuiz] = useState(null)
  const [stats, setStats] = useState(null)
  const [result, setResult] = useState(null)
  const [myAnswer, setMyAnswer] = useState(null)
  const quizRef = useRef(null)
  const hideTimer = useRef(null)

  const clientRef = useRef(null)
  const listRef = useRef(null)

  const push = (list) => setMessages((prev) => [...prev, ...list].slice(-MAX_MESSAGES))
  const notify = (content, type = 'SYSTEM') =>
    push([{ id: `local-${Date.now()}-${Math.random()}`, type, content }])

  const startQuiz = (q, s = null, answered = false) => {
    clearTimeout(hideTimer.current)
    quizRef.current = q
    setQuiz(q)
    setStats(s)
    setResult(null)
    setMyAnswer(answered ? -1 : null)
  }

  // 입장 시: 최근 채팅 + 얼리기 상태 + 진행 중 퀴즈 + 스티커 목록
  useEffect(() => {
    setMessages([])
    setQuiz(null)
    quizRef.current = null
    http('GET', `/api/channels/${channelCode}/chat`)
      .then((r) => {
        setFrozen({ chatFrozen: r.chatFrozen, stickerFrozen: r.stickerFrozen })
        // 소켓으로 먼저 들어온 메시지와 합치기 (중복 제거)
        setMessages((prev) => {
          const ids = new Set(r.messages.map((m) => m.id))
          return [...r.messages, ...prev.filter((m) => !ids.has(m.id))].slice(-MAX_MESSAGES)
        })
      })
      .catch(() => {})
    http('GET', `/api/channels/${channelCode}/quiz/active?clientId=${viewer.clientId}`)
      .then((r) => r.quiz && startQuiz(r.quiz, r.stats, r.myAnswered))
      .catch(() => {})
    http('GET', '/api/stickers').then(setStickers).catch(() => {})
    return () => clearTimeout(hideTimer.current)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelCode])

  // WebSocket
  useEffect(() => {
    const onEvent = (m) => {
      const p = m.payload || {}
      switch (m.type) {
        case 'CHAT':
        case 'STICKER':
        case 'ADMIN':
        case 'SYSTEM':
          push([m])
          break
        case 'CHAT_FREEZE':
          setFrozen({ chatFrozen: !!p.chatFrozen, stickerFrozen: !!p.stickerFrozen })
          if (p.chatFrozen || p.stickerFrozen) setPickerOpen(false)
          if (m.content) push([m])
          break
        case 'MESSAGE_DELETE':
        case 'MESSAGE_HIDE': {
          const ids = new Set(p.ids || [])
          setMessages((prev) => prev.filter((x) => !ids.has(x.id)))
          break
        }
        case 'MESSAGE_UNHIDE': {
          const restored = p.message
          if (!restored) break
          setMessages((prev) =>
            prev.some((x) => x.id === restored.id)
              ? prev
              : [...prev, restored].sort((a, b) => new Date(a.sentAt || 0) - new Date(b.sentAt || 0)).slice(-MAX_MESSAGES),
          )
          break
        }
        case 'QUIZ_START':
          startQuiz({ ...p, pushId: p.pushId ?? null, question: p.question ?? m.content })
          break
        case 'QUIZ_STATS':
          if (quizRef.current?.pushId === p.pushId) setStats({ counts: p.counts, total: p.total })
          break
        case 'QUIZ_RESULT':
          if (quizRef.current?.pushId === p.pushId) {
            setResult({ counts: p.counts, total: p.total, answerIndex: p.answerIndex })
            clearTimeout(hideTimer.current)
            hideTimer.current = setTimeout(() => {
              setQuiz(null)
              quizRef.current = null
            }, RESULT_LINGER_MS)
          }
          break
        case 'QUIZ_END':
          setQuiz(null)
          quizRef.current = null
          break
        default:
      }
    }

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/chat/${channelCode}`, (frame) => onEvent(JSON.parse(frame.body)))
        client.subscribe('/user/queue/errors', (frame) => notify(JSON.parse(frame.body).message, 'ERROR'))
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    clientRef.current = client
    return () => client.deactivate()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelCode])

  useEffect(() => {
    listRef.current?.scrollTo(0, listRef.current.scrollHeight)
  }, [messages])

  const publish = (body) => {
    if (!clientRef.current?.connected) return false
    clientRef.current.publish({
      destination: `/app/chat/${channelCode}`,
      body: JSON.stringify({ sender: viewer.nickname, clientId: viewer.clientId, ...body }),
    })
    return true
  }

  const send = (e) => {
    e.preventDefault()
    if (!text.trim() || frozen.chatFrozen) return
    if (publish({ content: text })) setText('')
  }

  const sendSticker = (s) => {
    if (frozen.chatFrozen || frozen.stickerFrozen) return
    publish({ stickerId: s.id })
    setPickerOpen(false)
  }

  const answer = async (i) => {
    if (!quiz?.pushId) return
    setMyAnswer(i)
    try {
      const r = await http('POST', `/api/quiz/${quiz.pushId}/answers`, {
        clientId: viewer.clientId,
        nickname: viewer.nickname,
        answerIndex: i,
      })
      setStats({ counts: r.counts, total: r.total })
    } catch (err) {
      if (!/이미 응답/.test(err.message)) setMyAnswer(null)
      notify(err.message, 'ERROR')
    }
  }

  const stickerDisabled = frozen.chatFrozen || frozen.stickerFrozen

  return (
    <aside className="chat">
      <div className="chat-head">
        채팅 {connected ? '' : '(연결 중...)'}
        {frozen.chatFrozen && <span className="frozen-tag">❄ 채팅 얼림</span>}
        {!frozen.chatFrozen && frozen.stickerFrozen && <span className="frozen-tag">스티커 중지</span>}
      </div>

      {quiz && (
        <QuizCard
          quiz={quiz}
          stats={stats}
          result={result}
          myAnswer={myAnswer}
          onAnswer={answer}
          onClose={result ? () => { setQuiz(null); quizRef.current = null } : undefined}
        />
      )}

      <ul className="chat-list" ref={listRef}>
        {messages.map((m) => <ChatLine key={m.id} m={m} />)}
      </ul>

      {pickerOpen && (
        <div className="sticker-picker">
          {stickers.map((s) => (
            <button key={s.id} type="button" className="sticker-btn" onClick={() => sendSticker(s)} title={s.name}>
              <StickerView sticker={s} size={40} />
            </button>
          ))}
          {stickers.length === 0 && <span className="muted">등록된 스티커가 없습니다.</span>}
        </div>
      )}

      <form className="chat-form" onSubmit={send}>
        <button
          type="button"
          className="sticker-toggle"
          onClick={() => setPickerOpen((o) => !o)}
          disabled={!connected || stickerDisabled}
          title={stickerDisabled ? '스티커를 사용할 수 없습니다' : '스티커'}
        >
          😊
        </button>
        <input
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder={frozen.chatFrozen ? '관리자가 채팅을 얼렸습니다' : `${viewer.nickname}(으)로 채팅`}
          maxLength={200}
          disabled={frozen.chatFrozen}
        />
        <button type="submit" disabled={!connected || frozen.chatFrozen}>전송</button>
      </form>
    </aside>
  )
}
