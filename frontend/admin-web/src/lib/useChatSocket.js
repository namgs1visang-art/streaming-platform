import { useEffect, useRef, useState } from 'react'
import { Client } from '@stomp/stompjs'

const WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8093/ws'

/**
 * 관리자용 채팅 채널 구독 (/topic/chat/{channelCode}).
 * 구독 헤더 role=admin → chat-server 가 시청자 수에서 제외한다.
 * @returns 연결 여부
 */
export function useChatSocket(channelCode, onEvent) {
  const [connected, setConnected] = useState(false)
  const handler = useRef(onEvent)
  handler.current = onEvent

  useEffect(() => {
    if (!channelCode) return
    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        setConnected(true)
        client.subscribe(`/topic/chat/${channelCode}`, (frame) => handler.current?.(JSON.parse(frame.body)), { role: 'admin' })
      },
      onWebSocketClose: () => setConnected(false),
    })
    client.activate()
    return () => {
      setConnected(false)
      client.deactivate()
    }
  }, [channelCode])

  return connected
}

export const VIEWER_URL = import.meta.env.VITE_VIEWER_URL || 'http://localhost:5174'
