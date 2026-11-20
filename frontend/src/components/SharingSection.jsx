import { useState } from 'react';

import { updateSharing } from '../api/expenses.js';

// Why the backend decided this player shares (or not), in words.
//   The automatic rule: still in the event, OR finished at least one match
//   (so someone who left early but played still pays their part).
function reasonText(player) {
  if (player.decidedByOrganizer) return 'Set by you';
  if (player.playedAMatch) return 'Automatic: played a match';
  if (player.active) return 'Automatic: in the event';
  return "Automatic: left without playing";
}

// The "Who shares the costs?" card: one checkbox per player.
//   eventId   = the event
//   players   = the backend's sharing rows (every player, also removed ones)
//   onChanged = called after a change, so the page loads fresh numbers
export default function SharingSection({ eventId, players, onChanged }) {
  // The id of the player being saved, or null.
  const [busyId, setBusyId] = useState(null);
  const [error, setError] = useState('');

  // sharesEqually = true / false, or null = back to the automatic rule.
  async function change(player, sharesEqually) {
    setBusyId(player.participantId);
    setError('');
    try {
      await updateSharing(eventId, player.participantId, sharesEqually);
      onChanged();
    } catch (changeError) {
      setError(changeError.message);
    }
    setBusyId(null);
  }

  return (
    <section className="card section" aria-labelledby="sharing-heading">
      <h2 id="sharing-heading">Who shares the costs?</h2>
      <p className="muted">
        Ticked players split the costs equally. Untick someone who shouldn&apos;t pay a share (e.g. a coach).
      </p>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {players.length === 0 ? (
        <p className="muted">No players yet. Add them on the event page.</p>
      ) : (
        <ul className="sharing-list" aria-label="Who shares the costs">
          {players.map((player) => (
            <li key={player.participantId} className="sharing-row">
              {/* The whole label is the tap target: big and easy to hit. */}
              <label className="check-field">
                <input
                  type="checkbox"
                  checked={player.sharesEqually}
                  disabled={busyId === player.participantId}
                  onChange={(changeEvent) => change(player, changeEvent.target.checked)}
                />
                <span>
                  {player.playerName}
                  {!player.active && <span className="muted"> (removed)</span>}
                  <span className="field-hint">{reasonText(player)}</span>
                </span>
              </label>
              {/* Only after the organizer decided: a way back to the automatic rule. */}
              {player.decidedByOrganizer && (
                <button
                  type="button"
                  className="button small"
                  disabled={busyId === player.participantId}
                  onClick={() => change(player, null)}
                  aria-label={`Use the automatic rule for ${player.playerName}`}
                >
                  Automatic
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}