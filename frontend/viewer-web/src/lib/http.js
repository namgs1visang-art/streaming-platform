// viewer-api 호출 (vite proxy: /api → viewer-api)
export async function http(method, url, body) {
  const res = await fetch(url, {
    method,
    headers: body ? { 'Content-Type': 'application/json' } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  })
  if (!res.ok) {
    let message = `요청 실패 (${res.status})`
    try {
      message = (await res.json()).message || message
    } catch { /* 본문 없음 */ }
    throw new Error(message)
  }
  return res.status === 204 ? null : res.json()
}

export const fmtDuration = (sec) => {
  if (sec == null) return '-'
  const h = Math.floor(sec / 3600)
  const m = Math.floor((sec % 3600) / 60)
  const s = Math.floor(sec % 60)
  const mm = String(m).padStart(2, '0')
  const ss = String(s).padStart(2, '0')
  return h > 0 ? `${h}:${mm}:${ss}` : `${mm}:${ss}`
}
