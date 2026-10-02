import { useEffect, useState } from 'react'
import { api } from '../api.js'

const today = () => new Date().toLocaleDateString('sv-SE') // yyyy-MM-dd
const addDays = (d, n) => {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x.toLocaleDateString('sv-SE')
}
const fmt = (s) => s.replace('T', ' ').slice(0, 16)

export default function SchedulePage() {
  const [from, setFrom] = useState(today())
  const [channels, setChannels] = useState([])
  const [schedules, setSchedules] = useState([])
  const [form, setForm] = useState({ channelId: '', title: '', startAt: '', endAt: '', recordEnabled: true })
  const [error, setError] = useState('')
  const to = addDays(from, 6)

  const load = () => api.schedules.list(from, to).then(setSchedules).catch((e) => setError(e.message))

  useEffect(() => { api.channels.list().then(setChannels) }, [])
  useEffect(() => { load() }, [from])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.schedules.create({ ...form, channelId: Number(form.channelId) })
      setForm({ ...form, title: '', startAt: '', endAt: '' })
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const remove = async (id) => {
    if (!confirm('스케줄을 삭제할까요?')) return
    await api.schedules.remove(id)
    load()
  }

  return (
    <>
      <h2>스케줄 관리</h2>

      <form className="card form-row" onSubmit={submit}>
        <select value={form.channelId} onChange={(e) => setForm({ ...form, channelId: e.target.value })} required>
          <option value="">채널 선택</option>
          {channels.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
        </select>
        <input placeholder="방송 제목" value={form.title}
          onChange={(e) => setForm({ ...form, title: e.target.value })} required />
        <input type="datetime-local" value={form.startAt}
          onChange={(e) => setForm({ ...form, startAt: e.target.value })} required />
        <input type="datetime-local" value={form.endAt}
          onChange={(e) => setForm({ ...form, endAt: e.target.value })} required />
        <label className="check">
          <input type="checkbox" checked={form.recordEnabled}
            onChange={(e) => setForm({ ...form, recordEnabled: e.target.checked })} /> 녹화
        </label>
        <button type="submit">등록</button>
      </form>
      {error && <p className="error">{error}</p>}

      <div className="toolbar">
        <button className="ghost" onClick={() => setFrom(addDays(from, -7))}>◀ 이전 주</button>
        <strong>{from} ~ {to}</strong>
        <button className="ghost" onClick={() => setFrom(addDays(from, 7))}>다음 주 ▶</button>
      </div>

      <table className="table">
        <thead>
          <tr><th>채널</th><th>제목</th><th>시작</th><th>종료</th><th>녹화</th><th></th></tr>
        </thead>
        <tbody>
          {schedules.map((s) => (
            <tr key={s.id}>
              <td>{s.channelName}</td>
              <td>{s.title}</td>
              <td>{fmt(s.startAt)}</td>
              <td>{fmt(s.endAt)}</td>
              <td>{s.recordEnabled ? 'Y' : 'N'}</td>
              <td className="actions">
                <button className="ghost danger" onClick={() => remove(s.id)}>삭제</button>
              </td>
            </tr>
          ))}
          {schedules.length === 0 && (
            <tr><td colSpan="6" className="muted center">이 기간에 스케줄이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
    </>
  )
}
