import { useEffect, useState } from 'react'

/**
 * 퀴즈/투표 카드 — 라이브(응답 가능)와 다시보기(읽기 전용) 공용
 *  quiz    { pushId, title, question, options, mode: QUIZ|VOTE, closesAt? }
 *  stats   { counts, total }        실시간 집계 (QUIZ_STATS)
 *  result  { counts, total, answerIndex } 마감 결과 (QUIZ_RESULT)
 *  myAnswer 내가 고른 번호 (-1 = 응답했지만 번호 모름)
 *  remainSec 외부에서 남은 시간을 주는 경우(다시보기). 없으면 closesAt 으로 계산
 */
export default function QuizCard({ quiz, stats, result, myAnswer, onAnswer, onClose, remainSec, disabled }) {
  const live = useRemain(quiz.closesAt)
  const remain = remainSec ?? live
  const closed = !!result || remain === 0
  const vote = quiz.mode === 'VOTE'
  const answered = myAnswer !== undefined && myAnswer !== null
  const shown = result || stats
  const showBars = closed || answered
  const total = shown?.total ?? 0

  return (
    <div className={`quiz ${closed ? 'closed' : ''}`}>
      <div className="quiz-head">
        <span className={`quiz-badge ${vote ? 'vote' : ''}`}>{vote ? '투표' : '퀴즈'}</span>
        <span className="quiz-timer">{closed ? '마감' : remain != null ? `${remain}초` : ''}</span>
        {onClose && <button className="quiz-x" onClick={onClose} aria-label="닫기">✕</button>}
      </div>
      <strong className="quiz-q">Q. {quiz.question}</strong>

      {(quiz.options || []).map((o, i) => {
        const count = shown?.counts?.[i] ?? 0
        const pct = total > 0 ? Math.round((count * 100) / total) : 0
        const correct = closed && !vote && result?.answerIndex === i
        const wrongPick = closed && !vote && myAnswer === i && result?.answerIndex !== i
        return (
          <button
            key={i}
            type="button"
            className={`quiz-option ${myAnswer === i ? 'picked' : ''} ${correct ? 'correct' : ''} ${wrongPick ? 'wrong' : ''}`}
            disabled={disabled || closed || answered || !onAnswer || quiz.pushId == null}
            onClick={() => onAnswer?.(i)}
          >
            {showBars && <span className="quiz-bar" style={{ width: `${pct}%` }} />}
            <span className="quiz-label">{i + 1}. {o}</span>
            {showBars && <span className="quiz-pct">{pct}% · {count}</span>}
          </button>
        )
      })}

      <div className="quiz-foot">
        {closed
          ? vote
            ? `투표 종료 · ${total}명 참여`
            : result?.answerIndex != null
              ? `정답: ${result.answerIndex + 1}번 · ${total}명 참여` + (answered && myAnswer >= 0 ? (myAnswer === result.answerIndex ? ' · 정답입니다! 🎉' : ' · 아쉬워요') : '')
              : `마감 · ${total}명 참여`
          : answered
            ? `응답 완료 · ${total}명 참여 중`
            : onAnswer ? '보기를 눌러 참여하세요' : `${total}명 참여`}
      </div>
    </div>
  )
}

function useRemain(closesAt) {
  const calc = () => (closesAt ? Math.max(0, Math.ceil((new Date(closesAt).getTime() - Date.now()) / 1000)) : null)
  const [remain, setRemain] = useState(calc)
  useEffect(() => {
    setRemain(calc())
    if (!closesAt) return
    const t = setInterval(() => setRemain(calc()), 500)
    return () => clearInterval(t)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [closesAt])
  return remain
}
