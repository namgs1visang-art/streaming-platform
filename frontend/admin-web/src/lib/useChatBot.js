import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

const WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8093/ws'
const MIN_INTERVAL_MS = 500 // chat-server app.chat.min-interval-ms (세션별 도배 제한)

const WORDS = ['안녕하세요', 'ㅋㅋㅋㅋ', '오 대박', '이거 뭐예요?', '선생님 최고!', '재밌어요', '정답 2번!', '와아아',
  '👍👍', '소리 잘 들려요', '화면 잘 나와요', '질문 있어요', 'ㅎㅎ', '헐', '박수 👏', '좋아요', '다시 설명해 주세요', '굿굿']
const pick = (arr) => arr[Math.floor(Math.random() * arr.length)]
const randomText = () => Array.from({ length: 1 + Math.floor(Math.random() * 3) }, () => pick(WORDS)).join(' ')

/**
 * 채팅 속도 테스트용 봇. 시청자와 같은 경로(STOMP SEND /app/chat/{channelCode})로 임의 메시지를 보낸다.
 * 세션별 도배 제한(500ms)이 있으므로 목표 속도에 맞춰 봇(WebSocket 연결)을 여러 개 띄워 나눠 보낸다.
 * 구독은 하지 않으므로 시청자 수에는 잡히지 않는다.
 */
export function useChatBot(channelCode, ratePerSec) {
  const [running, setRunning] = useState(false)
  const [sent, setSent] = useState(0)
  const [bots, setBots] = useState(0)
  const sentRef = useRef(0)

  useEffect(() => {
    if (!running || !channelCode) return
    const botCount = Math.max(1, Math.ceil((ratePerSec * MIN_INTERVAL_MS) / 1000))
    const interval = (botCount * 1000) / ratePerSec // 봇 1개당 전송 간격 (>= 500ms)
    const clients = []
    const timers = []
    setBots(botCount)

    for (let i = 0; i < botCount; i++) {
      const name = `봇${String(i + 1).padStart(2, '0')}`
      let started = false
      const client = new Client({
        brokerURL: WS_URL,
        reconnectDelay: 3000,
        onConnect: () => {
          if (started) return // 재연결 시 타이머 중복 방지
          started = true
          // 봇끼리 전송 시점을 고르게 분산
          const start = setTimeout(() => {
            const t = setInterval(() => {
              if (!client.connected) return
              client.publish({
                destination: `/app/chat/${channelCode}`,
                body: JSON.stringify({ sender: name, clientId: `chatbot-${i}`, content: randomText() }),
              })
              sentRef.current += 1
            }, interval)
            timers.push(t)
          }, (interval / botCount) * i)
          timers.push(start)
        },
      })
      client.activate()
      clients.push(client)
    }

    const tick = setInterval(() => setSent(sentRef.current), 500)
    return () => {
      timers.forEach((t) => { clearTimeout(t); clearInterval(t) })
      clearInterval(tick)
      clients.forEach((c) => c.deactivate())
      setSent(sentRef.current)
      setBots(0)
    }
  }, [running, channelCode, ratePerSec])

  const start = () => {
    sentRef.current = 0
    setSent(0)
    setRunning(true)
  }

  return { running, sent, bots, start, stop: () => setRunning(false) }
}
