// 로그인 연동 전 임시 시청자 식별: 브라우저마다 clientId(UUID) + 닉네임을 만들어 보관
const KEY = 'sp.viewer'

const uuid = () =>
  globalThis.crypto?.randomUUID?.() ??
  'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16)
  })

export function getViewer() {
  try {
    const saved = JSON.parse(localStorage.getItem(KEY) || 'null')
    if (saved?.clientId && saved?.nickname) return saved
  } catch { /* 저장소 사용 불가 → 새로 생성 */ }
  const viewer = { clientId: uuid(), nickname: '시청자' + Math.floor(Math.random() * 9000 + 1000) }
  try {
    localStorage.setItem(KEY, JSON.stringify(viewer))
  } catch { /* 무시 */ }
  return viewer
}

export function setNickname(nickname) {
  const v = { ...getViewer(), nickname }
  try {
    localStorage.setItem(KEY, JSON.stringify(v))
  } catch { /* 무시 */ }
  return v
}
