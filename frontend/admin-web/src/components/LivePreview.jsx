import { useEffect, useRef, useState } from 'react'
import HlsPreview from './HlsPreview.jsx'
import { startWhep, webrtcSupported, whepUrl } from '../lib/whep.js'

/** 관리자 미리보기: WebRTC(WHEP) 우선, 실패하면 HLS. 현재 재생 방식을 좌상단에 표시 */
export default function LivePreview({ code, hlsSrc }) {
  const [mode, setMode] = useState(webrtcSupported ? 'webrtc' : 'hls')

  useEffect(() => setMode(webrtcSupported ? 'webrtc' : 'hls'), [code])

  return (
    <div className="preview-wrap">
      {mode === 'hls' ? <HlsPreview src={hlsSrc} /> : <WhepPreview url={whepUrl(code)} onFail={() => setMode('hls')} />}
      <span className="preview-mode">{mode === 'webrtc' ? 'WebRTC' : 'HLS'}</span>
    </div>
  )
}

function WhepPreview({ url, onFail }) {
  const ref = useRef(null)
  const failRef = useRef(onFail)
  failRef.current = onFail

  useEffect(() => startWhep(ref.current, url, () => failRef.current?.()), [url])

  return <video ref={ref} className="preview" controls autoPlay muted playsInline />
}
