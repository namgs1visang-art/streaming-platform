/** SRS WHEP 엔드포인트 (채널코드 → URL) */
const WHEP_BASE = import.meta.env.VITE_WHEP_URL || 'http://localhost:1985/rtc/v1/whep/'

export const whepUrl = (code) => `${WHEP_BASE}?app=live&stream=${encodeURIComponent(code)}`

/**
 * WebRTC(WHEP) 재생 시작. SDP offer 를 POST 하면 SRS 가 answer 를 돌려준다.
 * 연결 실패/일정 시간 내 영상 미수신이면 onFail 호출 (→ HLS 로 전환)
 * @returns 정리 함수
 */
export function startWhep(video, url, onFail) {
  const pc = new RTCPeerConnection()
  const stream = new MediaStream()
  let done = false
  const fail = (reason) => {
    if (done) return
    done = true
    console.warn('[WHEP] 실패 → HLS 로 전환:', reason)
    onFail?.(reason)
  }

  pc.addTransceiver('video', { direction: 'recvonly' })
  pc.addTransceiver('audio', { direction: 'recvonly' })
  pc.ontrack = (e) => {
    stream.addTrack(e.track)
    if (video.srcObject !== stream) video.srcObject = stream
  }
  pc.onconnectionstatechange = () => {
    if (pc.connectionState === 'failed') fail('connection failed')
  }

  const timer = setTimeout(() => {
    if (stream.getVideoTracks().length === 0 || pc.connectionState !== 'connected') fail('timeout')
  }, 6000)

  ;(async () => {
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    const res = await fetch(url, { method: 'POST', headers: { 'Content-Type': 'application/sdp' }, body: offer.sdp })
    if (!res.ok) throw new Error(`HTTP ${res.status}`)
    await pc.setRemoteDescription({ type: 'answer', sdp: await res.text() })
  })().catch((e) => fail(e.message))

  return () => {
    done = true
    clearTimeout(timer)
    pc.close()
    video.srcObject = null
  }
}

export const webrtcSupported = typeof window !== 'undefined' && 'RTCPeerConnection' in window
