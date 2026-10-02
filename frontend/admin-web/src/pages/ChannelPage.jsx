import { useEffect, useState } from 'react'
import { api } from '../api.js'

const EMPTY = { code: '', name: '', description: '' }

export default function ChannelPage() {
  const [channels, setChannels] = useState([])
  const [form, setForm] = useState(EMPTY)
  const [error, setError] = useState('')

  const load = () => api.channels.list().then(setChannels).catch((e) => setError(e.message))

  useEffect(() => {
    load()
    const t = setInterval(load, 5000) // LIVE/OFFLINE 상태 갱신
    return () => clearInterval(t)
  }, [])

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    try {
      await api.channels.create(form)
      setForm(EMPTY)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const regenerate = async (id) => {
    if (!confirm('스트림키를 재발급하면 기존 OBS 설정은 더 이상 송출할 수 없습니다. 계속할까요?')) return
    await api.channels.regenerateKey(id)
    load()
  }

  const remove = async (id) => {
    if (!confirm('채널을 삭제할까요?')) return
    try {
      await api.channels.remove(id)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const copy = (text) => navigator.clipboard?.writeText(text)

  return (
    <>
      <h2>채널 관리</h2>

      <form className="card form-row" onSubmit={submit}>
        <input placeholder="채널 코드 (예: channel1)" value={form.code}
          onChange={(e) => setForm({ ...form, code: e.target.value })} required />
        <input placeholder="채널명" value={form.name}
          onChange={(e) => setForm({ ...form, name: e.target.value })} required />
        <input placeholder="설명" value={form.description}
          onChange={(e) => setForm({ ...form, description: e.target.value })} />
        <button type="submit">채널 생성</button>
      </form>
      {error && <p className="error">{error}</p>}

      <table className="table">
        <thead>
          <tr>
            <th>상태</th><th>코드</th><th>채널명</th>
            <th>OBS 서버</th><th>OBS 스트림 키</th><th>재생 URL</th><th></th>
          </tr>
        </thead>
        <tbody>
          {channels.map((c) => (
            <tr key={c.id}>
              <td><span className={`badge ${c.status === 'LIVE' ? 'live' : ''}`}>{c.status}</span></td>
              <td>{c.code}</td>
              <td>{c.name}</td>
              <td className="mono" onClick={() => copy(c.obsServer)} title="클릭하여 복사">{c.obsServer}</td>
              <td className="mono" onClick={() => copy(c.obsStreamKey)} title="클릭하여 복사">
                {c.obsStreamKey.replace(/key=(.{6}).*/, 'key=$1••••••')}
              </td>
              <td className="mono">{c.playbackUrl}</td>
              <td className="actions">
                <button className="ghost" onClick={() => regenerate(c.id)}>키 재발급</button>
                <button className="ghost danger" onClick={() => remove(c.id)}>삭제</button>
              </td>
            </tr>
          ))}
          {channels.length === 0 && (
            <tr><td colSpan="7" className="muted center">등록된 채널이 없습니다.</td></tr>
          )}
        </tbody>
      </table>
      <p className="muted">OBS: 설정 → 방송 → 서비스 "사용자 지정" → 서버 / 스트림 키 칸에 위 값을 붙여넣기 (클릭 시 복사)</p>
    </>
  )
}
