import { Link } from 'react-router';

import { courtsLabel, MATCH_FORMAT_LABELS } from '../events/eventLabels.js';
import { formatEventTime, startsToday } from '../utils/dates.js';
import StatusBadge from './StatusBadge.jsx';

// One event in the "My Events" list. The whole card is a link to the event's page.
export default function EventCard({ event }) {
  const isActive = event.status === 'ACTIVE';

  return (
    <Link to={`/events/${event.id}`} className="card event-card">
      <div className="event-card-top">
        <h2>{event.name}</h2>
        {/* Active events get a yellow "Today" badge on the day itself; the others
            show their status, since they appear in the Completed / Cancelled lists. */}
        {isActive && startsToday(event.startsAt) && <span className="badge today">Today</span>}
        {!isActive && <StatusBadge status={event.status} />}
      </div>
      <p>{formatEventTime(event.startsAt, event.endsAt)}</p>
      <p className="muted">{event.location}</p>
      <p className="muted">
        {courtsLabel(event.courtCount)} · {MATCH_FORMAT_LABELS[event.matchFormat]}
      </p>
    </Link>
  );
}