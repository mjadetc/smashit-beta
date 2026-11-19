import { api } from './client.js';
import { eventPath } from './events.js';

// Backend calls for an event's players ("participants" in the backend).
// Players are just names, not user accounts. They live inside one event:
//   /api/events/5/participants/12

// GET /api/events/5/participants -> the event's players, A to Z.
export function participantsPath(eventId) {
  return `${eventPath(eventId)}/participants`;
}

// POST -> 201 with the new player. If a player with that name was removed earlier,
// the backend brings the SAME player back (their match history is kept).
export function addParticipant(eventId, playerName) {
  return api(participantsPath(eventId), { method: 'POST', body: { playerName } });
}

// PUT -> the renamed player.
export function renameParticipant(eventId, participantId, playerName) {
  return api(`${participantsPath(eventId)}/${participantId}`, {
    method: 'PUT',
    body: { playerName },
  });
}

// DELETE -> 204. A "soft delete": they leave the list, but past matches still count.
export function removeParticipant(eventId, participantId) {
  return api(`${participantsPath(eventId)}/${participantId}`, { method: 'DELETE' });
}