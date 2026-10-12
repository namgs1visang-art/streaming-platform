import { useEffect, useRef, useState } from 'react'
import OvenPlayer from 'ovenplayer'
import Hls from 'hls.js'

// OvenPlayer 는 LL-HLS 재생에 전역 Hls(hls.js) 를 사용한다
if (typeof window !== 'undefined' && !window.Hls) window.Hls = Hls

const RETRY_MS = 3000

/**
 * OME 라이브 재생 (OvenPlayer)
 *  WebRTC(지연 1초 미만) 우선 → 실패하면 LL-HLS(2~5초) 로 자동 전환, 둘 다 실패하면 3초 뒤 재시도.
 *  화질(원본/720p/480p)은 회선에 따라 자동 전환되고, 플레이어 설정(톱니바퀴)에서 고를 수도 있다.
 *  주소창에 ?hls 를 붙이면 LL-HLS 로만 재생 (지연 비교용)
 * @param onProtocol 실제 재생 중인 방식('WebRTC' | 'LL-HLS')을 알려준다
 */
export default function OmePlayer({ webrtcUrl, hlsUrl, className = 'video', onProtocol }) {
  const hostRef = useRef(null)
  const protocolRef = useRef(onProtocol)
  protocolRef.current = onProtocol
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const host = hostRef.current
    if (!host || !hlsUrl) return
    // OvenPlayer 가 컨테이너 DOM 을 직접 바꾸므로 React 가 관리하지 않는 내부 요소에 만든다
    const el = document.createElement('div')
    host.appendChild(el)

    const forceHls = new URLSearchParams(window.location.search).has('hls')
    const sources = [
      ...(forceHls || !webrtcUrl ? [] : [{ label: 'WebRTC', type: 'webrtc', file: webrtcUrl }]),
      { label: 'LL-HLS', type: 'hls', file: hlsUrl },
    ]
    const player = OvenPlayer.create(el, {
      autoStart: true,
      mute: true, // 브라우저 자동재생 정책: 음소거여야 자동 시작 (플레이어에서 소리 켜기)
      autoFallback: true,
      showBigPlayButton: false,
      sources,
      hlsConfig: { lowLatencyMode: true, liveSyncDurationCount: 2, maxLiveSyncPlaybackRate: 1.2 },
    })

    let retry = null
    player.on('stateChanged', ({ newstate }) => {
      if (newstate !== 'playing') return
      const current = player.getSources()?.[player.getCurrentSource()]
      protocolRef.current?.(current?.label ?? null)
    })
    player.on('error', (e) => {
      console.warn('[OvenPlayer] 재생 실패 → 재시도', e)
      protocolRef.current?.(null)
      retry = setTimeout(() => setAttempt((n) => n + 1), RETRY_MS)
    })

    return () => {
      clearTimeout(retry)
      try {
        player.remove()
      } catch {
        // 이미 정리된 경우
      }
      host.innerHTML = ''
    }
  }, [webrtcUrl, hlsUrl, attempt])

  return <div ref={hostRef} className={className} />
}
