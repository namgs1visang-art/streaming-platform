import { Link, NavLink, Route, Routes } from 'react-router-dom'
import HomePage from './pages/HomePage.jsx'
import WatchPage from './pages/WatchPage.jsx'
import ScheduleBoardPage from './pages/ScheduleBoardPage.jsx'

export default function App() {
  return (
    <>
      <header className="header">
        <Link to="/" className="brand">STREAM</Link>
        <nav>
          <NavLink to="/" end>라이브</NavLink>
          <NavLink to="/schedule">편성표</NavLink>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/schedule" element={<ScheduleBoardPage />} />
        <Route path="/watch/:code" element={<WatchPage />} />
      </Routes>
    </>
  )
}
