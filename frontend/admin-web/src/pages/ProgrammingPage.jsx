import { useEffect, useState } from 'react'
import { api } from '../api.js'

const today = () => new Date().toLocaleDateString('sv-SE') // yyyy-MM-dd
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x.toLocaleDateString('sv-SE')
}
const fmt = (s) => s.replace('T', ' ').slice(0, 16)
const EMPTY = { channelId: '', title: '', startAt: '', endAt: '', recordEnabled: true }

const statusOf = (s, now) => {
  if (now < new Date(s.startAt).getTime()) return { label: '예정', cls: 'quiz' }
  if (now < new Date(s.endAt).getTime()) return { label: '편성 시간', cls: 'live' }
  return { label: '종료', cls: '' }
}

/**
 * 관리자 > LIVE > 편성 관리
 * 채널별 방송 편성(제목·시작·종료·녹화 여부) 등록/수정/삭제. 스케줄 관리 타임라인은 이 데이터를 보여준다.
 */
export default function ProgrammingPage() {
  const [from, setFrom] = useState(today())
  const [channelFilter, setChannelFilter] = useState('')
  const [channels, setChannels] = useState([])
  const [schedules, setSchedules] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [editingId, setEditingId] = useState(null)
  const [error, setError] = useState('')
  const to = addDays(from, 6)

  const load = () => api.schedules.list(from, to).then(setSchedules).catch((e) => setError(e.message))

  useEffect(() => { api.channels.list().then(setChannels).catch((e) => setError(e.message)) }, [])
  useEffect(() => { load() }, [from])

  const reset = () => {
    setForm(EMPTY)
    setEditingId(null)
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const { channelId, ...body } = form
    try {
      if (editingId) {
        await api.schedules.update(editingId, body)
        reset()
      } else {
        await api.schedules.create({ ...form, channelId: Number(channelId) })
        setForm({ ...form, title: '', startAt: '', endAt: '' })
      }
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const edit = (s) => {
    setError('')
    setEditingId(s.id)
    setForm({
      channelId: String(s.channelId),
      title: s.title,
      startAt: s.startAt.slice(0, 16),
      endAt: s.endAt.slice(0, 16),
      recordEnabled: s.recordEnabled,
    })
  }

  const remove = async (id) => {
    if (!confirm('편성을 삭제할까요?')) return
    try {
      await api.schedules.remove(id)
      if (editingId === id) reset()
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const now = Date.now()
  const rows = channelFilter ? schedules.filter((s) => String(s.channelId) === channelFilter) : schedules

  return (
    <>
      <h2>편성 관리</h2>

      <form className="card form-row" onSubmit={submit}>
        <select value={form.channelId} disabled={!!editingId}
          onChange={(e) => setForm({ ...form, channelId: e.target.value })} required>
          <option value="">채널 선택</option>
          {channels.map((c) => <option key={c.id} value={c.id}>{c.name} ({c.code})</option>)}
        </select>
        <input placeholder="방송 제목" value={form.title} maxLength={200}
          onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        <input type="datetime-local" value={form.startAt}
          onChange={(e) => setForm({ ...form, startAt: e.target.value })} required />
        <input type="datetime-local" value={form.endAt}
          onChange={(e) => setForm({ ...form, endAt: e.target.value })} required />
        <label className="check">
          <input type="checkbox" checked={form.recordEnabled}
            onChange={(e) => setForm({ ...form, recordEnabled: e.target.checked })} /> 녹화
        </label>
        <button type="submit">{editingId ? '수정' : '등록'}</button>
        {editingId && <button type="button" className="ghost" onClick={reset}>취소</button>}
      </form>
      {error && <p className="error">{error}</p>}

      <div className="toolbar">
        <button className="ghost" onClick={() => setFrom(addDays(from, -7))}>◀ 이전 주</button>
        <strong>{from} ~ {to}</strong>
        <button className="ghost" onClick={() => setFrom(addDays(from, 7))}>다음 주 ▶</button>
        <button className="ghost" onClick={() => setFrom(today())}>오늘</button>
        <span className="spacer" />
        <select className="search" value={channelFilter} onChange={(e) => setChannelFilter(e.target.value)}>
          <option value="">전체 채널</option>
          {channels.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
      </div>

      <table className="table">
        <thead>
          <tr><th>상태</th><th>채널</th><th>제목</th><th>시작</th><th>종료</th><th>녹화</th><th></th></tr>
        </thead>
        <tbody>
          {rows.map((s) => {
            const st = statusOf(s, now)
            return (
              <tr key={s.id} className={editingId === s.id ? 'editing' : ''}>
                <td><span className={`badge ${st.cls}`}>{st.label}</span></td>
                <td>{s.channelName}</td>
                <td>{s.title}</td>
                <td>{fmt(s.startAt)}</td>
                <td>{fmt(s.endAt)}</td>
                <td>{s.recordEnabled ? 'Y' : 'N'}</td>
                <td className="actions">
                  <button className="ghost" onClick={() => edit(s)}>수정</button>
                  <button className="ghost danger" onClick={() => remove(s.id)}>삭제</button>
                </td>
              </tr>
            )
          })}
          {rows.length === 0 && (
            <tr><td colSpan="7" className="muted center">이 기간에 편성이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
    </>
  )
}
