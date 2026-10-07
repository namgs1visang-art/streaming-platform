import { useEffect, useState } from 'react'
import { api } from '../api.js'

const EMPTY = { name: '', emoji: '', imageUrl: '', enabled: true, sortOrder: 0 }

/** 관리자 > 채팅 관리 > 스티커 관리 — 시청자 채팅창 스티커 선택창에 노출 */
export default function StickerPage() {
  const [list, setList] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [editId, setEditId] = useState(null)
  const [error, setError] = useState('')

  const load = () => api.stickers.list().then(setList).catch((e) => setError(e.message))
  useEffect(() => { load() }, [])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      const body = { ...form, sortOrder: Number(form.sortOrder) || 0 }
      if (editId) await api.stickers.update(editId, body)
      else await api.stickers.create(body)
      setForm({ ...EMPTY, sortOrder: list.length })
      setEditId(null)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const edit = (s) => {
    setEditId(s.id)
    setForm({ name: s.name, emoji: s.emoji || '', imageUrl: s.imageUrl || '', enabled: s.enabled, sortOrder: s.sortOrder })
  }

  const toggle = async (s) => {
    await api.stickers.update(s.id, { ...s, enabled: !s.enabled })
    load()
  }

  const remove = async (id) => {
    if (!confirm('스티커를 삭제할까요? (이미 보낸 채팅에는 그대로 남습니다)')) return
    try {
      await api.stickers.remove(id)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  return (
    <>
      <h2>스티커 관리</h2>
      <form className="card form-row" onSubmit={submit}>
        <input placeholder="이름 (예: 박수)" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        <input placeholder="이모지 (예: 👏)" value={form.emoji} onChange={(e) => setForm({ ...form, emoji: e.target.value })} style={{ minWidth: 100 }} />
        <input placeholder="또는 이미지 URL (png/gif)" value={form.imageUrl} onChange={(e) => setForm({ ...form, imageUrl: e.target.value })} style={{ minWidth: 260 }} />
        <input type="number" placeholder="순서" value={form.sortOrder} onChange={(e) => setForm({ ...form, sortOrder: e.target.value })} style={{ minWidth: 80, width: 80 }} />
        <label className="check">
          <input type="checkbox" checked={form.enabled} onChange={(e) => setForm({ ...form, enabled: e.target.checked })} /> 사용
        </label>
        <button type="submit">{editId ? '수정' : '등록'}</button>
        {editId && <button type="button" className="ghost" onClick={() => { setEditId(null); setForm(EMPTY) }}>취소</button>}
      </form>
      {error && <p className="error">{error}</p>}

      <table className="table">
        <thead>
          <tr><th>미리보기</th><th>이름</th><th>이모지</th><th>이미지 URL</th><th>순서</th><th>사용</th><th></th></tr>
        </thead>
        <tbody>
          {list.map((s) => (
            <tr key={s.id} className={s.enabled ? '' : 'moderated'}>
              <td>{s.imageUrl ? <img src={s.imageUrl} alt={s.name} className="sticker-md" /> : <span className="sticker-big">{s.emoji}</span>}</td>
              <td>{s.name}</td>
              <td>{s.emoji}</td>
              <td className="mono">{s.imageUrl}</td>
              <td>{s.sortOrder}</td>
              <td><button className="ghost" onClick={() => toggle(s)}>{s.enabled ? 'ON' : 'OFF'}</button></td>
              <td className="actions">
                <button className="ghost" onClick={() => edit(s)}>수정</button>
                <button className="ghost danger" onClick={() => remove(s.id)}>삭제</button>
              </td>
            </tr>
          ))}
          {list.length === 0 && <tr><td colSpan="7" className="muted center">스티커가 없습니다.</td></tr>}
        </tbody>
      </table>
      <p className="muted">채팅 메시지 관리 화면의 [스티커 얼리기]로 방송 중 스티커 전송만 막을 수 있습니다.</p>
    </>
  )
}
