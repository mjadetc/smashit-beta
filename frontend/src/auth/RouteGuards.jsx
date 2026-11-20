import { Navigate, useLocation } from 'react-router';

import { useAuth } from './AuthContext.js';

// Shown while we're still asking the backend who is logged in (usually a split second).
function Checking() {
  return <p className="page-status">Loading…</p>;
}

// Wraps pages that need a login. Not logged in -> go to /login, and remember where
// they were going ("from"), so the login page can send them back there afterwards.
export function RequireAuth({ children }) {
  const { user, checking } = useAuth();
  const location = useLocation();

  if (checking) return <Checking />;
  if (!user) {
    // replace = don't add /events to the history, so "Back" doesn't loop.
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return children;
}

// Wraps the login and register pages. Already logged in -> go to My Events.
export function RequireGuest({ children }) {
  const { user, checking } = useAuth();

  if (checking) return <Checking />;
  if (user) return <Navigate to="/events" replace />;
  return children;
}