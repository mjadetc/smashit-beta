import { Link } from 'react-router';

// Shown for any URL that doesn't match a route, e.g. /evnts (a typo).
export default function NotFoundPage() {
  return (
    <main className="auth-page">
      <div className="card auth-card">
        <h1>Page not found</h1>
        <p className="muted">That page doesn't exist.</p>
        <Link to="/events" className="button primary wide">
          Go to My Events
        </Link>
      </div>
    </main>
  );
}