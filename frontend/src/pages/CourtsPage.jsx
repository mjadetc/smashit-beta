import { Link, useParams } from 'react-router';

import { eventPath } from '../api/events.js';
import { matchesPath, queuePath } from '../api/matches.js';
import CourtCard from '../components/CourtCard.jsx';
import NextUpList from '../components/NextUpList.jsx';
import { useApiData } from '../hooks/useApiData.js';

// "Unfinished" matches: they occupy a court (and their 4 players).
const OPEN_STATUSES = ['SCHEDULED', 'IN_PROGRESS'];

// /events/5/courts: the live screen at the venue. One card per court, plus "Next up".
export default function CourtsPage() {
  const { eventId } = useParams();

  // Three things to load. Each useApiData call loads on its own, at the same time.
  const eventData = useApiData(eventPath(eventId));
  const matchesData = useApiData(matchesPath(eventId));
  const queueData = useApiData(queuePath(eventId));

  // A court card changed a match: show the new version straight away, then
  // ask the backend for the queue again (who is free has changed).
  function handleMatchChanged(updated) {
    matchesData.setData((matches) =>
      matches.some((m) => m.id === updated.id)
        ? matches.map((m) => (m.id === updated.id ? updated : m)) // replace it
        : [...matches, updated], // a new match: add it
    );
    queueData.reload();
  }

  // The first error, if any (e.g. "Event not found.").
  const error = eventData.error ?? matchesData.error ?? queueData.error;
  const loading = eventData.loading || matchesData.loading || queueData.loading;

  if (error) {
    return (
      <section>
        <Link to={`/events/${eventId}`} className="back-link">
          ← Back to the event
        </Link>
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  if (loading) return <p className="page-status">Loading courts…</p>;

  const event = eventData.data;
  const matches = matchesData.data;
  const queue = queueData.data;
  const canChange = event.status === 'ACTIVE';

  // Array.from({ length: 3 }, (_, i) => i + 1) makes [1, 2, 3]: one number per court.
  const courtNumbers = Array.from({ length: event.courtCount }, (_, i) => i + 1);

  return (
    <section>
      <Link to={`/events/${eventId}`} className="back-link">
        ← {event.name}
      </Link>
      <div className="page-header">
        <h1>Courts</h1>
        {/* Reload by hand, e.g. after changing players on another phone. */}
        <button
          type="button"
          className="button"
          onClick={() => {
            matchesData.reload();
            queueData.reload();
          }}
        >
          Refresh
        </button>
      </div>

      {!canChange && (
        <p className="notice" role="status">
          This event isn&apos;t active, so matches can&apos;t be changed. Reactivate it on the event page.
        </p>
      )}

      <div className="court-grid">
        {courtNumbers.map((courtNumber) => (
          <CourtCard
            key={courtNumber}
            eventId={event.id}
            courtNumber={courtNumber}
            match={matches.find((m) => m.courtNumber === courtNumber && OPEN_STATUSES.includes(m.status)) ?? null}
            playedCount={matches.filter((m) => m.courtNumber === courtNumber && m.status === 'COMPLETED').length}
            freePlayers={queue}
            canChange={canChange}
            onChanged={handleMatchChanged}
          />
        ))}
      </div>

      <NextUpList queue={queue} />
    </section>
  );
}