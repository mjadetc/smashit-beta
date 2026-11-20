import { useEffect, useRef, useState } from 'react';

import {
  addParticipant,
  participantsPath,
  removeParticipant,
  renameParticipant,
} from '../api/participants.js';
import { useApiData } from '../hooks/useApiData.js';

// Matches are doubles: 2 against 2.
const PLAYERS_PER_MATCH = 4;

// A to Z, ignoring upper/lower case ("ana" next to "Ana"), like the backend's list.
function sortByName(players) {
  return [...players].sort((a, b) =>
    a.playerName.localeCompare(b.playerName, undefined, { sensitivity: 'base' }),
  );
}

// One short sentence about whether there are enough players, e.g.
// "Enough for 2 matches at once · 1 resting". Courts limit how many matches can run.
function readinessText(playerCount, courtCount) {
  if (playerCount < PLAYERS_PER_MATCH) {
    const missing = PLAYERS_PER_MATCH - playerCount;
    return `Add ${missing} more ${missing === 1 ? 'player' : 'players'} for a doubles match.`;
  }
  const matches = Math.min(Math.floor(playerCount / PLAYERS_PER_MATCH), courtCount);
  const resting = playerCount - matches * PLAYERS_PER_MATCH;
  const matchText = matches === 1 ? '1 match' : `${matches} matches`;
  return resting > 0 ? `Enough for ${matchText} at once · ${resting} resting` : `Enough for ${matchText} at once`;
}

// The "Players" card on the event page: the list, plus add / rename / remove.
//   event = the event (we need its id, status and number of courts)
export default function PlayersSection({ event }) {
  const isActive = event.status === 'ACTIVE';
  const { data, loading, error, setData } = useApiData(participantsPath(event.id));
  const players = data ?? [];

  // The id of the player being renamed (only one at a time), or null.
  const [editingId, setEditingId] = useState(null);
  // The id of the player being saved or removed right now, or null.
  const [busyId, setBusyId] = useState(null);
  // A message for a failed remove (e.g. "...is in a match that hasn't finished.").
  const [listError, setListError] = useState('');

  // Called by AddPlayerForm with the player the backend returned.
  function handleAdded(player) {
    // filter first: a player brought back after being removed must not appear twice.
    setData(sortByName([...players.filter((p) => p.id !== player.id), player]));
  }

  // Called by PlayerRow. Returns normally on success, throws an ApiError on failure.
  async function handleRename(player, newName) {
    setBusyId(player.id);
    try {
      const renamed = await renameParticipant(event.id, player.id, newName);
      setData(sortByName(players.map((p) => (p.id === renamed.id ? renamed : p))));
      setEditingId(null);
    } finally {
      // "finally" runs whether it worked or threw, so the row never stays "busy".
      setBusyId(null);
    }
  }

  async function handleRemove(player) {
    const question =
      `Remove ${player.playerName} from this event? Their past matches are kept, ` +
      'and adding the same name again brings them back.';
    if (!window.confirm(question)) return;

    setBusyId(player.id);
    setListError('');
    try {
      await removeParticipant(event.id, player.id);
      setData(players.filter((p) => p.id !== player.id));
    } catch (removeError) {
      setListError(removeError.message);
    }
    setBusyId(null);
  }

  return (
    // aria-labelledby: screen readers call this region "Players", after its heading.
    <section className="card section" aria-labelledby="players-heading">
      <div className="section-header">
        <h2 id="players-heading">Players</h2>
        {!loading && !error && <span className="count-pill">{players.length}</span>}
      </div>

      {!loading && !error && (
        <p className="muted">{readinessText(players.length, event.courtCount)}</p>
      )}

      {isActive ? (
        <AddPlayerForm eventId={event.id} onAdded={handleAdded} />
      ) : (
        <p className="muted">Reactivate the event to change its players.</p>
      )}

      {loading && <p className="muted">Loading players…</p>}
      {error && (
        <p className="form-error" role="alert">
          {error.message}
        </p>
      )}
      {listError && (
        <p className="form-error" role="alert">
          {listError}
        </p>
      )}

      {players.length > 0 && (
        // aria-label so the list can be found by name ("Players") in tests and screen readers.
        <ul className="player-list" aria-label="Players">
          {players.map((player) => (
            <PlayerRow
              key={player.id}
              player={player}
              canChange={isActive}
              editing={editingId === player.id}
              busy={busyId === player.id}
              onStartEdit={() => setEditingId(player.id)}
              onCancelEdit={() => setEditingId(null)}
              onRename={(newName) => handleRename(player, newName)}
              onRemove={() => handleRemove(player)}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

// The "Add a player" box. Keeps the cursor in the box after each add,
// so the organizer can type name, Enter, name, Enter... as people arrive.
function AddPlayerForm({ eventId, onAdded }) {
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const [adding, setAdding] = useState(false);
  // useRef = a box that keeps a value between renders without causing a re-render.
  // Here it holds the <input> element itself, so we can call .focus() on it.
  const inputRef = useRef(null);

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    setNameError('');
    setAdding(true);
    try {
      const added = await addParticipant(eventId, name);
      onAdded(added);
      setName('');
    } catch (addError) {
      // 400 -> "Player name is required." (a field error)
      // 409 -> "A participant named "Juan" is already in this event." (the main message)
      setNameError(addError.fieldErrors.playerName ?? addError.message);
    }
    setAdding(false);
    inputRef.current?.focus(); // "?." = only if the input exists
  }

  return (
    <form className="field" onSubmit={handleSubmit} noValidate>
      <label htmlFor="new-player-name">Add a player</label>
      <div className="inline-form">
        <input
          id="new-player-name"
          ref={inputRef}
          value={name}
          onChange={(changeEvent) => setName(changeEvent.target.value)}
          placeholder="Player name"
          maxLength={80}
          autoComplete="off"
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? 'new-player-name-error' : undefined}
        />
        <button type="submit" className="button primary" disabled={adding}>
          {adding ? 'Adding…' : 'Add'}
        </button>
      </div>
      {nameError && (
        <p id="new-player-name-error" className="field-error">
          {nameError}
        </p>
      )}
    </form>
  );
}

// One player in the list: their name with Rename / Remove, or a rename box while editing.
function PlayerRow({ player, canChange, editing, busy, onStartEdit, onCancelEdit, onRename, onRemove }) {
  if (editing) {
    return (
      <li className="player-row">
        <RenameForm player={player} busy={busy} onRename={onRename} onCancel={onCancelEdit} />
      </li>
    );
  }

  return (
    <li className="player-row">
      {/* A round badge with the first letter, so names are easy to scan.
          aria-hidden: screen readers skip it, since the name follows anyway. */}
      <span className="avatar" aria-hidden="true">
        {player.playerName.charAt(0).toUpperCase()}
      </span>
      <span className="player-name">{player.playerName}</span>
      {canChange && (
        <span className="row-actions">
          {/* aria-label gives each button a full name ("Rename Juan"), because
              "Rename" alone is unclear when a screen reader lists all the buttons. */}
          <button
            type="button"
            className="button small"
            onClick={onStartEdit}
            disabled={busy}
            aria-label={`Rename ${player.playerName}`}
          >
            Rename
          </button>
          <button
            type="button"
            className="button small danger"
            onClick={onRemove}
            disabled={busy}
            aria-label={`Remove ${player.playerName}`}
          >
            {busy ? 'Removing…' : 'Remove'}
          </button>
        </span>
      )}
    </li>
  );
}

// The rename box: Enter or "Save" saves, Escape or "Cancel" goes back.
function RenameForm({ player, busy, onRename, onCancel }) {
  const [name, setName] = useState(player.playerName);
  const [nameError, setNameError] = useState('');
  const inputRef = useRef(null);
  const errorId = `rename-${player.id}-error`;

  // useEffect with [] runs once, after the box first appears: put the cursor in it
  // and select the old name, so typing replaces it.
  useEffect(() => {
    inputRef.current.focus();
    inputRef.current.select();
  }, []);

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    // Nothing changed: just close, no need to ask the backend.
    if (name.trim() === player.playerName) {
      onCancel();
      return;
    }
    setNameError('');
    try {
      await onRename(name);
    } catch (renameError) {
      setNameError(renameError.fieldErrors.playerName ?? renameError.message);
    }
  }

  function handleKeyDown(keyEvent) {
    if (keyEvent.key === 'Escape') onCancel();
  }

  return (
    <form className="rename-form" onSubmit={handleSubmit} noValidate>
      <div className="inline-form">
        <input
          ref={inputRef}
          value={name}
          onChange={(changeEvent) => setName(changeEvent.target.value)}
          onKeyDown={handleKeyDown}
          maxLength={80}
          autoComplete="off"
          aria-label={`New name for ${player.playerName}`}
          aria-invalid={nameError ? true : undefined}
          aria-describedby={nameError ? errorId : undefined}
        />
        <button type="submit" className="button small primary" disabled={busy}>
          {busy ? 'Saving…' : 'Save'}
        </button>
        <button type="button" className="button small" onClick={onCancel} disabled={busy}>
          Cancel
        </button>
      </div>
      {nameError && (
        <p id={errorId} className="field-error">
          {nameError}
        </p>
      )}
    </form>
  );
}