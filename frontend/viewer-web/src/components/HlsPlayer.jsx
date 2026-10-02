import { useEffect, useRef } from 'react'
import Hls from 'hls.js'

/** SRS 가 만든 HLS(.m3u8) 재생. Safari 는 기본 지원, 나머지는 hls.js 사용 */
export default function HlsPlayer({ src }) {
  const videoRef = useRef(null)

  useEffect(() => {
    const video = videoRef.current
    if (!video || !src) return

    if (video.canPlayType('application/vnd.apple.mpegurl')) {
      video.src = src
      return
    }
    if (!Hls.isSupported()) return

    const hls = new Hls({ liveSyncDurationCount: 3, lowLatencyMode: true })
    hls.loadSource(src)
    hls.attachMedia(video)
    hls.on(Hls.Events.ERROR, (_, data) => {
      // 송출 직후 m3u8 가 아직 없으면 404 → 잠시 후 재시도
      if (data.fatal && data.type === Hls.ErrorTypes.NETWORK_ERROR) {
        setTimeout(() => hls.loadSource(src), 3000)
      } else if (data.fatal) {
        hls.recoverMediaError()
      }
    })
    return () => hls.destroy()
  }, [src])

  return <video ref={videoRef} className="video" controls autoPlay muted playsInline />
}
