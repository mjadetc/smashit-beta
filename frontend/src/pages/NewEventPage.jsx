import { Link, useNavigate } from 'react-router';

import { createEvent } from '../api/events.js';
import EventForm from '../components/EventForm.jsx';

// /events/new: create an event, then open its page.
export default function NewEventPage() {
  const navigate = useNavigate();

  // EventForm calls this with the request body. If createEvent throws,
  // the error goes back to EventForm, which shows the messages.
  async function handleCreate(details) {
    const created = await createEvent(details);
    navigate(`/events/${created.id}`, { replace: true });
  }

  return (
    <section className="narrow">
      <Link to="/events" className="back-link">
        ← My Events
      </Link>
      <h1>New event</h1>
      <EventForm
        onSubmit={handleCreate}
        submitLabel="Create event"
        submittingLabel="Creating…"
        cancelTo="/events"
      />
    </section>
  );
}