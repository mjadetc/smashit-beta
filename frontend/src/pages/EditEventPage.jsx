import { Link, useNavigate, useParams } from 'react-router';

import { eventPath, updateEvent } from '../api/events.js';
import EventForm from '../components/EventForm.jsx';
import { useApiData } from '../hooks/useApiData.js';

// /events/5/edit: load the event, show the form filled in, save with PUT.
export default function EditEventPage() {
  // useParams reads the ":eventId" part of the URL (see App.jsx).
  const { eventId } = useParams();
  const navigate = useNavigate();
  const { data: event, loading, error } = useApiData(eventPath(eventId));

  async function handleSave(details) {
    await updateEvent(eventId, details);
    // replace: "Back" from the event page then skips this form.
    navigate(`/events/${eventId}`, { replace: true });
  }

  if (loading) return <p className="page-status">Loading event…</p>;

  if (error) {
    return (
      <section className="narrow">
        <Link to="/events" className="back-link">
          ← My Events
        </Link>
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  return (
    <section className="narrow">
      <Link to={`/events/${eventId}`} className="back-link">
        ← {event.name}
      </Link>
      <h1>Edit event</h1>

      {/* The backend only allows editing ACTIVE events (409 otherwise),
          so we say it up front instead of letting them fill in the form first. */}
      {event.status === 'ACTIVE' ? (
        <EventForm
          event={event}
          onSubmit={handleSave}
          submitLabel="Save changes"
          submittingLabel="Saving…"
          cancelTo={`/events/${eventId}`}
        />
      ) : (
        <p className="form-error" role="alert">
          Only active events can be edited. Reactivate it first.
        </p>
      )}
    </section>
  );
}