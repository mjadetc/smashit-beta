import { api } from './client.js';
import { matchesPath } from './matches.js';

// Backend calls for scoring one match on the court screen.
// EVERY call returns the whole scoreboard (score, server, positions, games,
// and "nextStep"), so the screen simply redraws from the answer.
//   /api/events/5/matches/40/...

function scoringPath(eventId, matchId) {
  return `${matchesPath(eventId)}/${matchId}`;
}

// GET -> the scoreboard. Works for any match status.
export function scoreboardPath(eventId, matchId) {
  return `${scoringPath(eventId, matchId)}/scoreboard`;
}

// POST -> the next game starts. servingTeam = 'A' or 'B'; serverId / receiverId = participant ids.
export function startGame(eventId, matchId, servingTeam, serverId, receiverId) {
  return api(`${scoringPath(eventId, matchId)}/games`, {
    method: 'POST',
    body: { servingTeam, serverId, receiverId },
  });
}

// POST -> one rally won by team 'A' or 'B'.
export function recordPoint(eventId, matchId, team) {
  return api(`${scoringPath(eventId, matchId)}/points`, { method: 'POST', body: { team } });
}

// POST -> removes the latest rally (or a game start with no rallies yet).
export function undoLast(eventId, matchId) {
  return api(`${scoringPath(eventId, matchId)}/undo`, { method: 'POST' });
}

// POST -> confirms the result: the match becomes COMPLETED and counts for matchmaking.
export function completeMatch(eventId, matchId) {
  return api(`${scoringPath(eventId, matchId)}/complete`, { method: 'POST' });
}