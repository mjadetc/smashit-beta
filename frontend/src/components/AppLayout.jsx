import { useState } from 'react';
import { Link, Outlet } from 'react-router';

import { useAuth } from '../auth/AuthContext.js';

// The frame around every logged-in page: a header bar, then the page itself.
// <Outlet /> is where React Router draws the current child page (e.g. My Events).
export default function AppLayout() {
  const { user, logout } = useAuth();
  const [loggingOut, setLoggingOut] = useState(false);

  async function handleLogout() {
    setLoggingOut(true);
    // logout() forgets the user even if the request fails, and RequireAuth then
    // sends us to /login. So there's nothing else to do here.
    await logout().catch(() => {});
  }

  return (
    <>
      <header className="app-header">
        <Link to="/events" className="brand">
          SmasHIT
        </Link>
        <div className="header-user">
          <span className="muted">{user.firstName}</span>
          <button type="button" className="button" onClick={handleLogout} disabled={loggingOut}>
            Log out
          </button>
        </div>
      </header>
      <main className="page">
        <Outlet />
      </main>
    </>
  );
}