import { useAuth } from '../auth/AuthContext.js';

// PLACEHOLDER for step 2: the real "My Events" list comes next.
// For now it proves that login works and the protected area is reachable.
export default function EventsPage() {
  const { user } = useAuth();

  return (
    <section>
      <h1>My Events</h1>
      <p className="muted">
        Hi {user.firstName}! Your events will appear here.
      </p>
    </section>
  );
}