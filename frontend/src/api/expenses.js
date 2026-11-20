import { api } from './client.js';
import { eventPath } from './events.js';

// Backend calls for an event's money: expenses, contributions, who shares the costs,
// and the summary (totals, each share, who pays whom).
//   /api/events/5/expenses/9
//   /api/events/5/contributions/3
//
// All the money maths happens in the BACKEND (in centavos, so nothing gets lost to
// rounding). After a change, the page simply asks for the summary again.

// ---------- expenses ----------

// GET -> every expense, earliest date first.
export function expensesPath(eventId) {
  return `${eventPath(eventId)}/expenses`;
}

// The body for creating or editing an expense. Exactly ONE payer:
//   { ..., paidById: 12 }                                          a player paid
//   { ..., outsidePayerName: 'Coach Jun', outsidePayerRepaid: true }  someone else, paid back
//   { ..., outsidePayerName: 'Coach Jun', outsidePayerRepaid: false } someone else, a gift
// POST -> 201 with the new expense.
export function createExpense(eventId, expense) {
  return api(expensesPath(eventId), { method: 'POST', body: expense });
}

// PUT -> the edited expense.
export function updateExpense(eventId, expenseId, expense) {
  return api(`${expensesPath(eventId)}/${expenseId}`, { method: 'PUT', body: expense });
}

// DELETE -> 204. A real delete. Contributions "for" it are kept, without the label.
export function deleteExpense(eventId, expenseId) {
  return api(`${expensesPath(eventId)}/${expenseId}`, { method: 'DELETE' });
}

// ---------- the summary and its settings ----------

// GET -> totals, the equal share, each person's balance, and who pays whom.
export function expenseSummaryPath(eventId) {
  return `${expensesPath(eventId)}/summary`;
}

// PUT -> the NEW summary. shareRounding = 'NONE' | 'PESO_1' | 'PESO_5' | 'PESO_10'.
export function updateRounding(eventId, shareRounding) {
  return api(`${expensesPath(eventId)}/settings`, { method: 'PUT', body: { shareRounding } });
}

// GET -> every player (also removed ones) and whether they pay an equal share.
//   [{ participantId, playerName, active, playedAMatch, sharesEqually, decidedByOrganizer }]
export function sharingPath(eventId) {
  return `${expensesPath(eventId)}/sharing`;
}

// PUT -> that player's new sharing row. sharesEqually = true, false,
// or null = "back to the automatic rule" (active, or finished a match).
export function updateSharing(eventId, participantId, sharesEqually) {
  return api(`${sharingPath(eventId)}/${participantId}`, { method: 'PUT', body: { sharesEqually } });
}

// ---------- contributions ----------

// GET -> every contribution, oldest first.
//   [{ id, participant: { participantId, playerName }, amount, expenseId, expenseDescription, note }]
export function contributionsPath(eventId) {
  return `${eventPath(eventId)}/contributions`;
}

// POST -> 201. { participantId, amount, expenseId (optional), note (optional) }
export function createContribution(eventId, contribution) {
  return api(contributionsPath(eventId), { method: 'POST', body: contribution });
}

// DELETE -> 204.
export function deleteContribution(eventId, contributionId) {
  return api(`${contributionsPath(eventId)}/${contributionId}`, { method: 'DELETE' });
}