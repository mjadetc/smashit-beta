import { useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';

import {
  cancelEvent,
  completeEvent,
  deleteEvent,
  eventPath,
  reactivateEvent,
} from '../api/events.js';
import StatusBadge from '../components/StatusBadge.jsx';
import { courtsLabel, MATCH_FORMAT_LABELS } from '../events/eventLabels.js';
import { useApiData } from '../hooks/useApiData.js';
import { formatEventTime } from '../utils/dates.js';

// The "are you sure?" question for each button that changes the status.
const CONFIRMATIONS = {
  complete: 'Mark this event as completed? Matches that haven\'t finished will be cancelled.',
  cancel: 'Cancel this event? Matches that haven\'t finished will be cancelled. You can reactivate it later.',
  delete: 'Delete this event for good? Its players, matches and expenses are deleted too. This can\'t be undone.',
};

// /events/5: the event's details and what the organizer can do with it.
// Steps 3-7 add Players, Courts, Stats and Expenses to this page.
export default function EventPage() {
  const { eventId } = useParams();
  const navigate = useNavigate();
  const { data: event, loading, error, setData } = useApiData(eventPath(eventId));

  // Which button is busy ('complete', 'delete'...), or null. Disables all buttons meanwhile.
  const [busyAction, setBusyAction] = useState(null);
  const [actionError, setActionError] = useState('');

  // Asks "are you sure?", then calls the backend.
  //   name   = 'complete' | 'cancel' | 'reactivate' | 'delete'
  //   change = the function from api/events.js to call
  async function runAction(name, change) {
    // window.confirm shows the browser's own OK / Cancel box, and returns true for OK.
    // Reactivating is harmless, so it doesn't ask.
    if (CONFIRMATIONS[name] && !window.confirm(CONFIRMATIONS[name])) {
      return;
    }

    setBusyAction(name);
    setActionError('');
    try {
      const updated = await change(eventId);
      if (name === 'delete') {
        // The event is gone: back to the list.
        navigate('/events', { replace: true });
        return;
      }
      // The backend returns the event with its new status: show it straight away.
      setData(updated);
    } catch (changeError) {
      setActionError(changeError.message);
    }
    setBusyAction(null);
  }

  if (loading) return <p className="page-status">Loading event…</p>;

  if (error) {
    return (
      <section>
        <Link to="/events" className="back-link">
          ← My Events
        </Link>
        {/* 404 -> "Event not found." (also for someone else's event: the backend
            doesn't reveal that it exists). */}
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  const isActive = event.status === 'ACTIVE';
  const busy = busyAction !== null;

  return (
    <section>
      {/* Back to the tab this event is listed in. */}
      <Link to={`/events?status=${event.status}`} className="back-link">
        ← My Events
      </Link>

      <div className="event-title">
        <h1>{event.name}</h1>
        <StatusBadge status={event.status} />
      </div>

      <div className="card">
        <dl className="detail-list">
          <dt>When</dt>
          <dd>{formatEventTime(event.startsAt, event.endsAt)}</dd>
          <dt>Where</dt>
          <dd>{event.location}</dd>
          <dt>Courts</dt>
          <dd>{courtsLabel(event.courtCount)}</dd>
          <dt>Format</dt>
          <dd>
            {MATCH_FORMAT_LABELS[event.matchFormat]}
            {event.changeEndsAt != null && `, change ends at ${event.changeEndsAt}`}
          </dd>
          {event.description && (
            <>
              <dt>Notes</dt>
              <dd>{event.description}</dd>
            </>
          )}
        </dl>

        {actionError && (
          <p className="form-error" role="alert">
            {actionError}
          </p>
        )}

        <div className="actions">
          {/* Active events can be edited, completed or cancelled... */}
          {isActive && (
            <>
              <Link to={`/events/${event.id}/edit`} className="button primary">
                Edit
              </Link>
              <button
                type="button"
                className="button"
                disabled={busy}
                onClick={() => runAction('complete', completeEvent)}
              >
                {busyAction === 'complete' ? 'Completing…' : 'Mark completed'}
              </button>
              <button
                type="button"
                className="button"
                disabled={busy}
                onClick={() => runAction('cancel', cancelEvent)}
              >
                {busyAction === 'cancel' ? 'Cancelling…' : 'Cancel event'}
              </button>
            </>
          )}

          {/* ...completed and cancelled ones can only be made active again. */}
          {!isActive && (
            <button
              type="button"
              className="button primary"
              disabled={busy}
              onClick={() => runAction('reactivate', reactivateEvent)}
            >
              {busyAction === 'reactivate' ? 'Reactivating…' : 'Reactivate'}
            </button>
          )}

          <button
            type="button"
            className="button danger"
            disabled={busy}
            onClick={() => runAction('delete', deleteEvent)}
          >
            {busyAction === 'delete' ? 'Deleting…' : 'Delete'}
          </button>
        </div>
      </div>

      <p className="muted section">Players, courts, stats and expenses will appear here next.</p>
    </section>
  );
}