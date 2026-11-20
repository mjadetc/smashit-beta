import { Navigate, Route, Routes } from 'react-router';

import AuthProvider from './auth/AuthProvider.jsx';
import { RequireAuth, RequireGuest } from './auth/RouteGuards.jsx';
import AppLayout from './components/AppLayout.jsx';
import EditEventPage from './pages/EditEventPage.jsx';
import EventPage from './pages/EventPage.jsx';
import EventsPage from './pages/EventsPage.jsx';
import LoginPage from './pages/LoginPage.jsx';
import NewEventPage from './pages/NewEventPage.jsx';
import NotFoundPage from './pages/NotFoundPage.jsx';
import RegisterPage from './pages/RegisterPage.jsx';
import CourtsPage from './pages/CourtsPage.jsx';
import ScoreboardPage from './pages/ScoreboardPage.jsx';

// The app's pages, by URL. Each <Route> says "at this path, show this component".
// The router itself (<BrowserRouter>) is added in main.jsx, so the tests can use a
// <MemoryRouter> instead (a router that doesn't need a real browser address bar).
export default function App() {
  return (
    <AuthProvider>
      <Routes>
        {/* "/" on its own: go to My Events (RequireAuth sends guests to /login). */}
        <Route path="/" element={<Navigate to="/events" replace />} />

        {/* Only for people who are NOT logged in. */}
        <Route path="/login" element={<RequireGuest><LoginPage /></RequireGuest>} />
        <Route path="/register" element={<RequireGuest><RegisterPage /></RequireGuest>} />

        {/* A "layout route": no path of its own. Every page inside it needs a login
            and is drawn inside AppLayout's header frame. */}
        <Route element={<RequireAuth><AppLayout /></RequireAuth>}>
          <Route path="/events" element={<EventsPage />} />
          {/* NEW in step 2. "/events/new" is matched before "/events/:eventId",
              because React Router prefers fixed text over a ":placeholder". */}
          <Route path="/events/new" element={<NewEventPage />} />
          {/* ":eventId" matches anything, e.g. /events/5; useParams() reads it. */}
          <Route path="/events/:eventId" element={<EventPage />} />
          <Route path="/events/:eventId/edit" element={<EditEventPage />} />
          {/* The live courts screen. */}
          <Route path="/events/:eventId/courts" element={<CourtsPage />} />
          {/* NEW in step 5: the court screen for scoring one match. */}
          <Route path="/events/:eventId/matches/:matchId" element={<ScoreboardPage />} />
        </Route>

        {/* "*" = any other URL. */}
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </AuthProvider>
  );
}