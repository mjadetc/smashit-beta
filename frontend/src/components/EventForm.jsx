import { useState } from 'react';
import { Link } from 'react-router';

import { MATCH_FORMAT_LABELS } from '../events/eventLabels.js';
import { fromLocalInputValue, toLocalInputValue } from '../utils/dates.js';
import FormField from './FormField.jsx';

// What a new event starts with. Everything is TEXT, because that's what inputs hold;
// toRequestBody() below turns it into the numbers and dates the backend expects.
const NEW_EVENT_FORM = {
  name: '',
  location: '',
  startsAt: '',
  endsAt: '',
  courtCount: '1',
  matchFormat: 'SINGLE_GAME', // recreational play: one game to 21
  changeEndsAt: '11', // the usual "switch sides at 11"
  description: '',
};

// An event from the backend -> the form's text values (for editing).
function toFormValues(event) {
  return {
    name: event.name,
    location: event.location,
    startsAt: toLocalInputValue(event.startsAt),
    endsAt: toLocalInputValue(event.endsAt),
    courtCount: String(event.courtCount),
    matchFormat: event.matchFormat,
    // null = "never switch ends", which the form shows as an empty box.
    changeEndsAt: event.changeEndsAt == null ? '' : String(event.changeEndsAt),
    // "??" = use the right side when the left is null or undefined.
    description: event.description ?? '',
  };
}

// "" -> null (the backend then says "... is required."), "2" -> 2.
function toNumberOrNull(text) {
  return text.trim() === '' ? null : Number(text);
}

// The form's text values -> the JSON body of EventRequest.
function toRequestBody(form) {
  return {
    name: form.name,
    location: form.location,
    startsAt: fromLocalInputValue(form.startsAt),
    endsAt: fromLocalInputValue(form.endsAt),
    courtCount: toNumberOrNull(form.courtCount),
    matchFormat: form.matchFormat,
    // Change of ends only exists for a single game; the backend rejects it otherwise.
    changeEndsAt: form.matchFormat === 'SINGLE_GAME' ? toNumberOrNull(form.changeEndsAt) : null,
    description: form.description.trim() === '' ? null : form.description,
  };
}

// The create / edit form. The page using it decides what happens on submit:
//   event        = the event being edited, or nothing for a new event
//   onSubmit     = async function that receives the request body and saves it
//                  (if it throws an ApiError, the form shows the messages)
//   submitLabel  = e.g. "Create event" / "Save changes"
//   cancelTo     = where the "Cancel" link goes
export default function EventForm({ event, onSubmit, submitLabel, submittingLabel, cancelTo }) {
  // useState(() => ...) runs the function only once, for the first render.
  const [form, setForm] = useState(() => (event ? toFormValues(event) : NEW_EVENT_FORM));
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [submitting, setSubmitting] = useState(false);

  function handleChange(changeEvent) {
    const { name, value } = changeEvent.target;
    setForm((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    setFieldErrors({});
    setFormError('');
    setSubmitting(true);

    try {
      await onSubmit(toRequestBody(form));
      // On success the page navigates away, so there's nothing to reset here.
    } catch (error) {
      // 400 with "errors" -> messages under the fields.
      // 400 without them ("End time must be after the start time.") or 409 -> one message.
      setFieldErrors(error.fieldErrors ?? {});
      if (Object.keys(error.fieldErrors ?? {}).length === 0) {
        setFormError(error.message);
      }
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="card">
      <FormField
        label="Event name"
        name="name"
        placeholder="Friday Night Smash"
        value={form.name}
        onChange={handleChange}
        error={fieldErrors.name}
      />
      <FormField
        label="Location"
        name="location"
        placeholder="Court Hub, Quezon City"
        value={form.location}
        onChange={handleChange}
        error={fieldErrors.location}
      />

      <div className="field-row">
        {/* datetime-local = the phone's own date and time picker. */}
        <FormField
          label="Starts"
          name="startsAt"
          type="datetime-local"
          value={form.startsAt}
          onChange={handleChange}
          error={fieldErrors.startsAt}
        />
        <FormField
          label="Ends"
          name="endsAt"
          type="datetime-local"
          value={form.endsAt}
          onChange={handleChange}
          error={fieldErrors.endsAt}
        />
      </div>

      <div className="field-row">
        {/* inputMode="numeric" shows a number keyboard on phones. */}
        <FormField
          label="Number of courts"
          name="courtCount"
          type="number"
          inputMode="numeric"
          min="1"
          max="50"
          value={form.courtCount}
          onChange={handleChange}
          error={fieldErrors.courtCount}
        />
        <FormField
          as="select"
          label="Match format"
          name="matchFormat"
          value={form.matchFormat}
          onChange={handleChange}
          error={fieldErrors.matchFormat}
        >
          {/* Object.entries turns { A: 'a', B: 'b' } into [['A','a'], ['B','b']]. */}
          {Object.entries(MATCH_FORMAT_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </FormField>
      </div>

      {/* Only shown for a single game: best of three always switches at 11 in game three. */}
      {form.matchFormat === 'SINGLE_GAME' && (
        <FormField
          label="Change ends at (points)"
          name="changeEndsAt"
          type="number"
          inputMode="numeric"
          min="1"
          max="29"
          hint="Players switch sides when one side reaches this score. Leave empty to never switch."
          value={form.changeEndsAt}
          onChange={handleChange}
          error={fieldErrors.changeEndsAt}
        />
      )}

      <FormField
        as="textarea"
        label="Description (optional)"
        name="description"
        placeholder="Bring your own racket. Shuttles provided."
        value={form.description}
        onChange={handleChange}
        error={fieldErrors.description}
      />

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="actions">
        <button type="submit" className="button primary" disabled={submitting}>
          {submitting ? submittingLabel : submitLabel}
        </button>
        <Link to={cancelTo} className="button">
          Cancel
        </Link>
      </div>
    </form>
  );
}