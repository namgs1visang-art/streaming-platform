import { useState } from 'react'
import { NavLink, Navigate, Route, Routes } from 'react-router-dom'
import { MENU } from './menu.js'
import ChannelPage from './pages/ChannelPage.jsx'
import SchedulePage from './pages/SchedulePage.jsx'
import Placeholder from './pages/Placeholder.jsx'
import ChatMessagePage from './pages/ChatMessagePage.jsx'
import StickerPage from './pages/StickerPage.jsx'
import QuizPage from './pages/QuizPage.jsx'
import QuizSchedulePage from './pages/QuizSchedulePage.jsx'
import RecordingPage from './pages/RecordingPage.jsx'

export default function App() {
  const [open, setOpen] = useState(() => Object.fromEntries(MENU.map((m) => [m.group, true])))

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="logo">Streaming<span>admin</span></div>
        {MENU.map((m) => (
          <div key={m.group} className="menu-group">
            <button
              className="menu-title"
              onClick={() => setOpen((o) => ({ ...o, [m.group]: !o[m.group] }))}
            >
              {m.group}
              <span>{open[m.group] ? '▴' : '▾'}</span>
            </button>
            {open[m.group] &&
              m.items.map((it) => (
                <NavLink key={it.path} to={it.path} className="menu-item">
                  {it.label}
                </NavLink>
              ))}
          </div>
        ))}
      </aside>

      <main className="content">
        <header className="topbar">
          <span>파일 업로드</span>
          <span>업로드 현황</span>
          <span>관리자</span>
        </header>
        <div className="page">
          <Routes>
            <Route path="/" element={<Navigate to="/live/channels" replace />} />
            <Route path="/live/channels" element={<ChannelPage />} />
            <Route path="/live/schedules" element={<SchedulePage />} />
            <Route path="/quiz/questions" element={<QuizPage />} />
            <Route path="/quiz/programming" element={<QuizSchedulePage />} />
            <Route path="/vod/recordings" element={<RecordingPage />} />
            <Route path="/chat/messages" element={<ChatMessagePage />} />
            <Route path="/chat/stickers" element={<StickerPage />} />
            {MENU.flatMap((m) => m.items)
              .filter((it) => !it.ready)
              .map((it) => (
                <Route key={it.path} path={it.path} element={<Placeholder title={it.label} />} />
              ))}
          </Routes>
        </div>
      </main>
    </div>
  )
}
