import { useEffect, useRef, useState } from 'react'
import { useParams, useSearchParams } from 'react-router-dom'
import { Client } from '@stomp/stompjs'
import { http } from '../lib/http.js'

const WS_URL = import.meta.env.VITE_CHAT_WS_URL || 'ws://localhost:8093/ws'
const RESULT_LINGER_MS = 12000 // 결과를 방송 화면에 띄워 두는 시간

/**
 * OBS 브라우저 소스용 퀴즈/투표 오버레이 (방송 화면에 합성)
 *   http://localhost:5174/overlay/{채널코드}/quiz
 *   옵션: ?pos=tr|tl|br|bl (위치, 기본 tr)  &scale=1.2 (크기)
 * 배경은 투명. 관리자가 [출제]하면 나타나고, 시청자 투표가 실시간 막대로 반영, 마감 후 결과 표시 뒤 사라진다.
 */
export default function OverlayQuizPage() {
  const { code } = useParams()
  const [sp] = useSearchParams()
  const pos = ['tr', 'tl', 'br', 'bl'].includes(sp.get('pos')) ? sp.get('pos') : 'tr'
  const scale = Number(sp.get('scale')) || 1

  const [quiz, setQuiz] = useState(null)
  const [stats, setStats] = useState({ counts: [], total: 0 })
  const [result, setResult] = useState(null)
  const [now, setNow] = useState(Date.now())
  const quizRef = useRef(null)
  const hideTimer = useRef(null)

  // 투명 배경
  useEffect(() => {
    document.documentElement.classList.add('overlay-mode')
    return () => document.documentElement.classList.remove('overlay-mode')
  }, [])

  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 250)
    return () => clearInterval(t)
  }, [])

  const show = (q, s) => {
    clearTimeout(hideTimer.current)
    quizRef.current = q
    setQuiz(q)
    setStats(s || { counts: (q.options || []).map(() => 0), total: 0 })
    setResult(null)
  }
  const hide = () => {
    quizRef.current = null
    setQuiz(null)
    setResult(null)
  }

  useEffect(() => {
    // 오버레이를 나중에 켜도 진행 중인 퀴즈 복원
    http('GET', `/api/channels/${code}/quiz/active`).then((r) => r.quiz && show(r.quiz, r.stats)).catch(() => {})

    const client = new Client({
      brokerURL: WS_URL,
      reconnectDelay: 3000,
      onConnect: () => {
        client.subscribe(`/topic/chat/${code}`, (frame) => {
          const m = JSON.parse(frame.body)
          const p = m.payload || {}
          if (m.type === 'QUIZ_START') show({ ...p, question: p.question ?? m.content })
          else if (m.type === 'QUIZ_STATS' && quizRef.current?.pushId === p.pushId) setStats({ counts: p.counts, total: p.total })
          else if (m.type === 'QUIZ_RESULT' && quizRef.current?.pushId === p.pushId) {
            setStats({ counts: p.counts, total: p.total })
            setResult({ answerIndex: p.answerIndex })
            clearTimeout(hideTimer.current)
            hideTimer.current = setTimeout(hide, RESULT_LINGER_MS)
          } else if (m.type === 'QUIZ_END') hide()
        }, { role: 'overlay' }) // 시청자 수에서 제외
      },
    })
    client.activate()
    return () => {
      clearTimeout(hideTimer.current)
      client.deactivate()
    }
  }, [code])

  if (!quiz) return null

  const vote = quiz.mode === 'VOTE'
  const remain = quiz.closesAt ? Math.max(0, Math.ceil((new Date(quiz.closesAt).getTime() - now) / 1000)) : null
  const closed = !!result || remain === 0
  const total = stats.total || 0
  const max = Math.max(0, ...(stats.counts || []))
  const limit = quiz.timeLimitSec || 1

  return (
    <div className={`ov-wrap ov-${pos}`} style={{ transform: `scale(${scale})` }}>
      <div className={`ov-card ${closed ? 'closed' : ''}`}>
        <div className="ov-head">
          <span className={`ov-badge ${vote ? 'vote' : ''}`}>{vote ? '투표' : '퀴즈'}</span>
          <span className="ov-title">{quiz.title}</span>
          <span className="ov-timer">{closed ? '마감' : `${remain}초`}</span>
        </div>
        {!closed && remain != null && (
          <div className="ov-progress"><span style={{ width: `${Math.min(100, (remain / limit) * 100)}%` }} /></div>
        )}
        <div className="ov-q">{quiz.question}</div>

        {(quiz.options || []).map((o, i) => {
          const c = stats.counts?.[i] ?? 0
          const pct = total > 0 ? Math.round((c * 100) / total) : 0
          const win = closed && (vote ? c === max && max > 0 : result?.answerIndex === i)
          return (
            <div key={i} className={`ov-opt ${win ? 'win' : ''} ${closed && !win ? 'dim' : ''}`}>
              <span className="ov-bar" style={{ width: `${pct}%` }} />
              <span className="ov-num">{i + 1}</span>
              <span className="ov-label">{o}</span>
              <span className="ov-pct">{pct}%</span>
            </div>
          )
        })}

        <div className="ov-foot">
          {closed
            ? vote ? `투표 종료 · ${total.toLocaleString()}명 참여`
              : `정답 ${result?.answerIndex != null ? result.answerIndex + 1 + '번' : ''} · ${total.toLocaleString()}명 참여`
            : `${total.toLocaleString()}명 참여 중 · 채팅창에서 선택하세요`}
        </div>
      </div>
    </div>
  )
}
