import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

const WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8083/ws'
const randomNick = () => '시청자' + Math.floor(Math.random() * 9000 + 1000)

/**
 * 채팅 + 퀴즈 이벤트 수신.
 * 구독: /topic/chat/{channelCode}   전송: /app/chat/{channelCode}
 */
export default function ChatPanel({ channelCode }) {
  const [messages, setMessages] = useState([])
  const [quiz, setQuiz] = useState(null)
  const [text, setText] = useState('')
  const [nick] = useState(randomNick)
  const [connected, setConnected] = useState(false)
  const clientRef = useRef(null)
  const listRef = useRef(null)

  useEffect(() => {
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/chat/${channelCode}`, (frame) => {
          const msg = JSON.parse(frame.body)
          if (msg.type === 'QUIZ_START') setQuiz(msg)
          else if (msg.type === 'QUIZ_END') setQuiz(null)
          setMessages((prev) => [...prev.slice(-199), msg]) // 최근 200개만 유지
        })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    clientRef.current = client
    return () => client.deactivate()
  }, [channelCode])

  useEffect(() => {
    listRef.current?.scrollTo(0, listRef.current.scrollHeight)
  }, [messages])

  const send = (e) => {
    e.preventDefault()
    if (!text.trim() || !clientRef.current?.connected) return
    clientRef.current.publish({
      destination: `/app/chat/${channelCode}`,
      body: JSON.stringify({ sender: nick, content: text }),
    })
    setText('')
  }

  return (
    <aside className="chat">
      <div className="chat-head">채팅 {connected ? '' : '(연결 중...)'}</div>

      {quiz && (
        <div className="quiz">
          <strong>Q. {quiz.content}</strong>
          {/* TODO(6단계): 보기 클릭 → 답안 제출 API */}
          {(quiz.payload?.options || []).map((o, i) => (
            <button key={i} className="quiz-option">{o}</button>
          ))}
        </div>
      )}

      <ul className="chat-list" ref={listRef}>
        {messages.map((m) => (
          <li key={m.id} className={m.type !== 'CHAT' ? 'system' : ''}>
            {m.type === 'CHAT' ? <><b>{m.sender}</b> {m.content}</> : <>📢 {m.content}</>}
          </li>
        ))}
      </ul>

      <form className="chat-form" onSubmit={send}>
        <input value={text} onChange={(e) => setText(e.target.value)} placeholder={`${nick}(으)로 채팅`} maxLength={200} />
        <button type="submit" disabled={!connected}>전송</button>
      </form>
    </aside>
  )
}
