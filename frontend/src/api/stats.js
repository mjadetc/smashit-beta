import { eventPath } from './events.js';

// Backend addresses for an event's statistics. Both are read-only (GET), so they're
// loaded with useApiData. The backend counts everything again on every request,
// from the COMPLETED matches only, so the numbers are always up to date.

// GET -> the standings: one line per player, most wins first, then fewest losses, then A to Z.
//   [{ participantId, playerName, active, matchesPlayed, wins, losses,
//      gamesWon, gamesLost, pointsWon, pointsLost }]
export function playerStatsPath(eventId) {
  return `${eventPath(eventId)}/player-stats`;
}

// GET -> the event at a glance: match counts, durations (in seconds), courts and
// the finished matches, most recent first.
export function summaryPath(eventId) {
  return `${eventPath(eventId)}/summary`;
}