import { Link, useSearchParams } from 'react-router';

import { EVENT_STATUSES, eventListPath } from '../api/events.js';
import EventCard from '../components/EventCard.jsx';
import { STATUS_LABELS } from '../events/eventLabels.js';
import { useApiData } from '../hooks/useApiData.js';

// What to say when a list is empty.
const EMPTY_MESSAGES = {
  ACTIVE: 'No upcoming events yet. Create one to start inviting players.',
  COMPLETED: 'No completed events yet.',
  CANCELLED: 'No cancelled events.',
};

// "My Events": the organizer's events, with tabs for Active / Completed / Cancelled.
export default function EventsPage() {
  // The selected tab lives in the URL (/events?status=COMPLETED), not in state.
  // So "Back" returns to the same tab, and the link can be bookmarked.
  const [searchParams] = useSearchParams();
  const requested = searchParams.get('status');
  // Anything unknown in the URL (or nothing) falls back to ACTIVE.
  const status = EVENT_STATUSES.includes(requested) ? requested : 'ACTIVE';

  const { data: events, loading, error } = useApiData(eventListPath(status));

  return (
    <section>
      <div className="page-header">
        <h1>My Events</h1>
        <Link to="/events/new" className="button primary">
          + New event
        </Link>
      </div>

      {/* <nav> + aria-label tells screen readers this is a set of navigation links. */}
      <nav className="tabs" aria-label="Event status">
        {EVENT_STATUSES.map((tabStatus) => (
          <Link
            key={tabStatus}
            to={`/events?status=${tabStatus}`}
            className="tab"
            // aria-current="page" = "this is the one you're on" (also used by the CSS).
            aria-current={tabStatus === status ? 'page' : undefined}
          >
            {STATUS_LABELS[tabStatus]}
          </Link>
        ))}
      </nav>

      {loading && <p className="page-status">Loading events…</p>}

      {error && (
        <p className="form-error" role="alert">
          {error.message}
        </p>
      )}

      {events && events.length === 0 && (
        <div className="card empty-state">
          <p className="muted">{EMPTY_MESSAGES[status]}</p>
          {status === 'ACTIVE' && (
            <Link to="/events/new" className="button primary">
              Create your first event
            </Link>
          )}
        </div>
      )}

      {events && events.length > 0 && (
        <ul className="event-list">
          {/* "key" helps React tell the items apart when the list changes. */}
          {events.map((event) => (
            <li key={event.id}>
              <EventCard event={event} />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}