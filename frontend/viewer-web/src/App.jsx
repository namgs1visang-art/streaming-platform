import { Link, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import HomePage from './pages/HomePage.jsx'
import WatchPage from './pages/WatchPage.jsx'
import ScheduleBoardPage from './pages/ScheduleBoardPage.jsx'
import ReplayListPage from './pages/ReplayListPage.jsx'
import ReplayPage from './pages/ReplayPage.jsx'
import OverlayQuizPage from './pages/OverlayQuizPage.jsx'

export default function App() {
  // OBS 오버레이는 헤더 없이 투명 화면만
  if (useLocation().pathname.startsWith('/overlay/')) {
    return (
      <Routes>
        <Route path="/overlay/:code/quiz" element={<OverlayQuizPage />} />
      </Routes>
    )
  }

  return (
    <>
      <header className="header">
        <Link to="/" className="brand">STREAM</Link>
        <nav>
          <NavLink to="/" end>라이브</NavLink>
          <NavLink to="/schedule">편성표</NavLink>
          <NavLink to="/replay">다시보기</NavLink>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/schedule" element={<ScheduleBoardPage />} />
        <Route path="/watch/:code" element={<WatchPage />} />
        <Route path="/replay" element={<ReplayListPage />} />
        <Route path="/replay/:id" element={<ReplayPage />} />
      </Routes>
    </>
  )
}
