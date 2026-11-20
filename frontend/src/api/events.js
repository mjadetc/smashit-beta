import { api } from './client.js';

// Every backend call about events, with a readable name.
// Screens call createEvent(details) instead of remembering the URL and the method.
// Each one returns a Promise (use it with "await"), and throws an ApiError on failure.

// The three lists the backend can give: GET /api/events?status=ACTIVE etc.
export const EVENT_STATUSES = ['ACTIVE', 'COMPLETED', 'CANCELLED'];

// The URL of one event. Also used by useApiData() on the event pages.
export function eventPath(eventId) {
  return `/api/events/${eventId}`;
}

// The URL of the list with one status. ACTIVE ones come soonest first,
// COMPLETED / CANCELLED ones most recent first (the backend sorts them).
export function eventListPath(status) {
  return `/api/events?status=${status}`;
}

// POST /api/events -> the new event (with its id).
export function createEvent(details) {
  return api('/api/events', { method: 'POST', body: details });
}

// PUT /api/events/5 -> the updated event. PUT replaces everything, so send every field.
export function updateEvent(eventId, details) {
  return api(eventPath(eventId), { method: 'PUT', body: details });
}

// POST /api/events/5/complete | cancel | reactivate -> the event with its new status.
export function completeEvent(eventId) {
  return api(`${eventPath(eventId)}/complete`, { method: 'POST' });
}

export function cancelEvent(eventId) {
  return api(`${eventPath(eventId)}/cancel`, { method: 'POST' });
}

export function reactivateEvent(eventId) {
  return api(`${eventPath(eventId)}/reactivate`, { method: 'POST' });
}

// DELETE /api/events/5 -> nothing (204). The event, its players, matches and expenses are gone.
export function deleteEvent(eventId) {
  return api(eventPath(eventId), { method: 'DELETE' });
}