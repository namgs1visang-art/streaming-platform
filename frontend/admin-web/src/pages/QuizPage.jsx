import { useEffect, useState } from 'react'
import { api } from '../api.js'

const EMPTY = { title: '', question: '', options: ['', ''], answerIndex: 0, vote: false, timeLimitSec: 20 }

/** 관리자 > 퀴즈 관리 > 퀴즈 관리 (문제 은행) — 정답이 있는 퀴즈 / 정답 없는 투표 */
export default function QuizPage() {
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(0)
  const [data, setData] = useState({ items: [], total: 0, size: 20 })
  const [form, setForm] = useState(EMPTY)
  const [editId, setEditId] = useState(null)
  const [error, setError] = useState('')

  const load = (p = page) => api.quizzes.list({ keyword, page: p, size: 20 }).then(setData).catch((e) => setError(e.message))
  useEffect(() => { load(0) }, [])

  const setOption = (i, v) => setForm({ ...form, options: form.options.map((o, j) => (j === i ? v : o)) })
  const addOption = () => form.options.length < 5 && setForm({ ...form, options: [...form.options, ''] })
  const removeOption = (i) => {
    if (form.options.length <= 2) return
    const options = form.options.filter((_, j) => j !== i)
    setForm({ ...form, options, answerIndex: Math.min(form.answerIndex, options.length - 1) })
  }

  const submit = async (e) => {
    e.preventDefault()
    setError('')
    const body = {
      title: form.title,
      question: form.question,
      options: form.options,
      answerIndex: form.vote ? null : Number(form.answerIndex),
      timeLimitSec: Number(form.timeLimitSec),
    }
    try {
      if (editId) await api.quizzes.update(editId, body)
      else await api.quizzes.create(body)
      setForm(EMPTY)
      setEditId(null)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const edit = (q) => {
    setEditId(q.id)
    setForm({
      title: q.title, question: q.question, options: [...q.options],
      answerIndex: q.answerIndex ?? 0, vote: q.answerIndex == null, timeLimitSec: q.timeLimitSec,
    })
    window.scrollTo(0, 0)
  }

  const remove = async (id) => {
    if (!confirm('퀴즈를 삭제할까요?')) return
    try {
      await api.quizzes.remove(id)
      load()
    } catch (err) {
      setError(err.message)
    }
  }

  const pages = Math.max(1, Math.ceil(data.total / (data.size || 20)))

  return (
    <>
      <h2>퀴즈 관리</h2>

      <form className="card quiz-form" onSubmit={submit}>
        <div className="form-row">
          <label className="check"><input type="radio" checked={!form.vote} onChange={() => setForm({ ...form, vote: false })} /> 퀴즈 (정답 있음)</label>
          <label className="check"><input type="radio" checked={form.vote} onChange={() => setForm({ ...form, vote: true })} /> 투표 (정답 없음)</label>
        </div>
        <div className="form-row">
          <input placeholder="관리용 제목" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} required maxLength={100} />
          <input className="grow" placeholder="문제 / 투표 질문" value={form.question} onChange={(e) => setForm({ ...form, question: e.target.value })} required maxLength={500} />
          <label className="check">제한
            <input type="number" min={5} max={600} value={form.timeLimitSec} onChange={(e) => setForm({ ...form, timeLimitSec: e.target.value })} style={{ width: 80, minWidth: 80 }} />초
          </label>
        </div>
        {form.options.map((o, i) => (
          <div key={i} className="form-row option-row">
            {!form.vote && (
              <label className="check" title="정답">
                <input type="radio" name="answer" checked={Number(form.answerIndex) === i} onChange={() => setForm({ ...form, answerIndex: i })} /> 정답
              </label>
            )}
            <span>{i + 1}.</span>
            <input className="grow" placeholder={`보기 ${i + 1}`} value={o} onChange={(e) => setOption(i, e.target.value)} required maxLength={100} />
            <button type="button" className="ghost danger" onClick={() => removeOption(i)} disabled={form.options.length <= 2}>－</button>
          </div>
        ))}
        <div className="form-row">
          <button type="button" className="ghost" onClick={addOption} disabled={form.options.length >= 5}>＋ 보기 추가</button>
          <span className="spacer" />
          {editId && <button type="button" className="ghost" onClick={() => { setEditId(null); setForm(EMPTY) }}>취소</button>}
          <button type="submit">{editId ? '수정' : '등록'}</button>
        </div>
      </form>
      {error && <p className="error">{error}</p>}

      <form className="toolbar" onSubmit={(e) => { e.preventDefault(); setPage(0); load(0) }}>
        <input className="search" placeholder="제목/문제 검색" value={keyword} onChange={(e) => setKeyword(e.target.value)} />
        <button type="submit" className="ghost">검색</button>
        <span className="muted">총 {data.total}건</span>
      </form>

      <table className="table">
        <thead>
          <tr><th>유형</th><th>제목</th><th>문제</th><th>보기</th><th>제한</th><th></th></tr>
        </thead>
        <tbody>
          {data.items.map((q) => (
            <tr key={q.id}>
              <td><span className={`badge ${q.mode === 'VOTE' ? 'vote' : 'quiz'}`}>{q.mode === 'VOTE' ? '투표' : '퀴즈'}</span></td>
              <td>{q.title}</td>
              <td>{q.question}</td>
              <td className="options">
                {q.options.map((o, i) => (
                  <span key={i} className={q.answerIndex === i ? 'answer' : ''}>{i + 1}. {o}</span>
                ))}
              </td>
              <td>{q.timeLimitSec}초</td>
              <td className="actions">
                <button className="ghost" onClick={() => edit(q)}>수정</button>
                <button className="ghost danger" onClick={() => remove(q.id)}>삭제</button>
              </td>
            </tr>
          ))}
          {data.items.length === 0 && <tr><td colSpan="6" className="muted center">등록된 퀴즈가 없습니다.</td></tr>}
        </tbody>
      </table>

      <div className="toolbar">
        <button className="ghost" disabled={page === 0} onClick={() => { setPage(page - 1); load(page - 1) }}>◀ 이전</button>
        <span>{page + 1} / {pages}</span>
        <button className="ghost" disabled={page + 1 >= pages} onClick={() => { setPage(page + 1); load(page + 1) }}>다음 ▶</button>
      </div>
    </>
  )
}
