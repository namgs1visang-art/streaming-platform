/** 스티커 표시 (이미지가 있으면 이미지, 없으면 이모지) */
export function StickerView({ sticker, size = 48 }) {
  if (!sticker) return null
  return sticker.imageUrl ? (
    <img src={sticker.imageUrl} alt={sticker.name} title={sticker.name} className="sticker-img" style={{ width: size, height: size }} />
  ) : (
    <span className="sticker-emoji" style={{ fontSize: size * 0.75 }} title={sticker.name} role="img" aria-label={sticker.name}>
      {sticker.emoji}
    </span>
  )
}

/** 채팅 한 줄 — 라이브/다시보기 공용 */
export default function ChatLine({ m }) {
  switch (m.type) {
    case 'CHAT':
      return <li><b>{m.sender}</b> {m.content}</li>
    case 'STICKER':
      return (
        <li className="sticker-line">
          <b>{m.sender}</b>
          <StickerView sticker={m.payload} />
        </li>
      )
    case 'ADMIN':
      return <li className="admin"><span className="admin-tag">관리자</span> {m.content}</li>
    case 'ERROR':
      return <li className="error-line">⚠ {m.content}</li>
    default:
      return <li className="system">📢 {m.content}</li>
  }
}
