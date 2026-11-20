import { api } from './client.js';
import { eventPath } from './events.js';

// Backend calls for matches (one doubles match on one court) and the "next up" queue.
//   /api/events/5/matches/40

// GET -> every match of the event, oldest first (any status).
export function matchesPath(eventId) {
  return `${eventPath(eventId)}/matches`;
}

// GET -> the free players, fairest first: fewest matches played, then longest waiting.
export function queuePath(eventId) {
  return `${eventPath(eventId)}/queue`;
}

function matchPath(eventId, matchId) {
  return `${matchesPath(eventId)}/${matchId}`;
}

// POST -> 201 with the new SCHEDULED match.
//   teamA / teamB = the participant ids the organizer chose (0 to 2 each).
//   The backend fills every empty seat with the fairest free players.
export function createMatch(eventId, courtNumber, teamA = [], teamB = []) {
  return api(matchesPath(eventId), { method: 'POST', body: { courtNumber, teamA, teamB } });
}

// PUT -> the match with its new line-up (only before it starts). Empty seats are filled again.
export function updateLineup(eventId, matchId, teamA, teamB) {
  return api(`${matchPath(eventId, matchId)}/lineup`, { method: 'PUT', body: { teamA, teamB } });
}

// POST -> the match with new players in the seats the APP picked (the organizer's picks stay).
export function reshuffleMatch(eventId, matchId) {
  return api(`${matchPath(eventId, matchId)}/reshuffle`, { method: 'POST' });
}

// POST -> SCHEDULED becomes IN_PROGRESS; the line-up is locked.
export function startMatch(eventId, matchId) {
  return api(`${matchPath(eventId, matchId)}/start`, { method: 'POST' });
}

// POST -> CANCELLED. Frees the court and the players; it never counts as played.
export function cancelMatch(eventId, matchId) {
  return api(`${matchPath(eventId, matchId)}/cancel`, { method: 'POST' });
}