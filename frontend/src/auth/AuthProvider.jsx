import { useEffect, useState } from 'react';

import { api, loadCsrfToken, setUnauthorizedHandler } from '../api/client.js';
import { AuthContext } from './AuthContext.js';

// Knows who is logged in, and offers login / register / logout to every screen.
//   user     = the logged-in organizer ({ id, firstName, lastName, username, email }), or null
//   checking = true while we're still asking the backend "who am I?" when the app opens
export default function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(true);

  // useEffect with [] runs once, when the app first appears on screen.
  useEffect(() => {
    // Any 401 from any screen means the session is gone: forget the user, and
    // RequireAuth will send them to the login page.
    setUnauthorizedHandler(() => setUser(null));

    // In development, React's StrictMode runs effects twice on purpose (to find bugs).
    // "cancelled" makes sure only the latest run updates the state.
    let cancelled = false;

    async function checkSession() {
      try {
        // 1) Get the CSRF cookie, so later POSTs work.
        await loadCsrfToken();
        // 2) Ask who is logged in: 200 = the user, 401 = nobody (lands in catch).
        const me = await api('/api/auth/me');
        if (!cancelled) setUser(me);
      } catch {
        if (!cancelled) setUser(null);
      } finally {
        if (!cancelled) setChecking(false);
      }
    }
    checkSession();

    // The "cleanup" function: runs when the component goes away.
    return () => {
      cancelled = true;
      setUnauthorizedHandler(null);
    };
  }, []);

  // Logs in and remembers the user. Errors (wrong password...) go to the caller,
  // so the login form can show them.
  async function login(email, password) {
    const loggedIn = await api('/api/auth/login', {
      method: 'POST',
      body: { email, password },
    });
    // Logging in starts a new session, so fetch a fresh CSRF token for it.
    await loadCsrfToken();
    setUser(loggedIn);
    return loggedIn;
  }

  // Creates the account, then logs straight in with the same email and password,
  // so the organizer doesn't have to type them twice.
  async function register(details) {
    await api('/api/auth/register', { method: 'POST', body: details });
    return login(details.email, details.password);
  }

  async function logout() {
    try {
      await api('/api/auth/logout', { method: 'POST' });
    } finally {
      // Even if the request failed (e.g. no internet), forget the user on this screen.
      setUser(null);
      // The old session is gone; get a token for the next login. Ignore errors here.
      await loadCsrfToken().catch(() => {});
    }
  }

  return (
    <AuthContext.Provider value={{ user, checking, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  );
}