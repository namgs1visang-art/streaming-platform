import { useEffect, useRef } from 'react'
import Hls from 'hls.js'

/** 관리자 미리보기 플레이어 (음소거 자동재생) */
export default function HlsPreview({ src }) {
  const ref = useRef(null)

  useEffect(() => {
    const video = ref.current
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
      if (data.fatal && data.type === Hls.ErrorTypes.NETWORK_ERROR) setTimeout(() => hls.loadSource(src), 3000)
      else if (data.fatal) hls.recoverMediaError()
    })
    return () => hls.destroy()
  }, [src])

  return <video ref={ref} className="preview" controls autoPlay muted playsInline />
}
