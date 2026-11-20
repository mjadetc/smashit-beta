import { useState } from 'react';

import {
  cancelMatch,
  createMatch,
  reshuffleMatch,
  startMatch,
  updateLineup,
} from '../api/matches.js';
import { formatTime } from '../utils/dates.js';
import LineupPicker from './LineupPicker.jsx';

// One team's two names, e.g. "Ana & Leo". Seats the app picked get a small "auto" tag,
// so the organizer knows "Reshuffle" may change them.
function TeamNames({ label, seats }) {
  return (
    <div className="team">
      <span className="team-label">{label}</span>
      <ul className="team-players">
        {seats.map((seat) => (
          <li key={seat.participantId}>
            {seat.playerName}
            {seat.autoAssigned && <span className="auto-tag">auto</span>}
          </li>
        ))}
      </ul>
    </div>
  );
}

// One court on the Courts page. It shows one of three states:
//   free        -> "Auto-pick players" or "Choose players"
//   SCHEDULED   -> the line-up, with Start / Reshuffle / Change line-up / Cancel
//   IN_PROGRESS -> who is playing, with Cancel
// Props:
//   eventId, courtNumber
//   match       = the court's unfinished match, or null when the court is free
//   playedCount = how many matches this court has finished
//   freePlayers = the "next up" queue, to choose players from
//   canChange   = false for a completed / cancelled event (look, don't touch)
//   onChanged   = called with the match the backend returned after every change,
//                 so the page can show it straight away (and reload the queue)
export default function CourtCard({ eventId, courtNumber, match, playedCount, freePlayers, canChange, onChanged }) {
  // 'create' | 'lineup' while the picker is open, otherwise null.
  const [picker, setPicker] = useState(null);
  // The name of the button that is busy ('start', 'cancel'...), or null.
  const [busyAction, setBusyAction] = useState(null);
  const [error, setError] = useState('');

  // Runs one backend call for a button, then hands the updated match to the page.
  async function run(name, change) {
    setBusyAction(name);
    setError('');
    try {
      const updated = await change();
      onChanged(updated);
    } catch (changeError) {
      setError(changeError.message);
    }
    setBusyAction(null);
  }

  function handleCancelMatch() {
    const question = match.status === 'IN_PROGRESS'
      ? `Cancel the match on court ${courtNumber}? It's in progress; its score won't count.`
      : `Cancel the match on court ${courtNumber}? The players go back to the queue.`;
    if (window.confirm(question)) {
      run('cancel', () => cancelMatch(eventId, match.id));
    }
  }

  // The picker's onSubmit. Errors are NOT caught here: LineupPicker shows them.
  async function handlePickerSubmit(teamA, teamB) {
    const updated = picker === 'create'
      ? await createMatch(eventId, courtNumber, teamA, teamB)
      : await updateLineup(eventId, match.id, teamA, teamB);
    setPicker(null);
    onChanged(updated);
  }

  const busy = busyAction !== null;
  const status = match?.status ?? 'FREE'; // "?." and "??": no match -> 'FREE'
  const BADGES = {
    FREE: { className: 'free', text: 'Free' },
    SCHEDULED: { className: 'scheduled', text: 'Up next' },
    IN_PROGRESS: { className: 'playing', text: 'Playing' },
  };

  // Who the picker can offer: the free players, plus (when changing a line-up)
  // the players already in this match, who aren't in the queue because they're "busy" here.
  const pickable = match
    ? [...match.teamA, ...match.teamB, ...freePlayers]
    : freePlayers;

  return (
    // aria-labelledby: this region is called "Court 1", after its heading.
    <section className={`card court-card ${BADGES[status].className}`} aria-labelledby={`court-${courtNumber}`}>
      <div className="court-header">
        <h2 id={`court-${courtNumber}`}>Court {courtNumber}</h2>
        <span className={`badge court-${BADGES[status].className}`}>{BADGES[status].text}</span>
        <span className="muted court-played">{playedCount} played</span>
      </div>

      {match && !picker && (
        <div className="matchup">
          <TeamNames label="Team A" seats={match.teamA} />
          <span className="versus" aria-hidden="true">vs</span>
          <TeamNames label="Team B" seats={match.teamB} />
        </div>
      )}

      {status === 'IN_PROGRESS' && !picker && (
        <p className="muted">
          Started at {formatTime(match.startedAt)}. Live scoring is coming next.
        </p>
      )}
      {status === 'FREE' && !picker && <p className="muted">Ready for the next match.</p>}

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {picker && (
        <LineupPicker
          players={pickable}
          initialTeamA={picker === 'lineup' ? match.teamA.map((seat) => seat.participantId) : []}
          initialTeamB={picker === 'lineup' ? match.teamB.map((seat) => seat.participantId) : []}
          onSubmit={handlePickerSubmit}
          submitLabel={picker === 'create' ? 'Create match' : 'Save line-up'}
          onCancel={() => setPicker(null)}
        />
      )}

      {canChange && !picker && (
        <div className="actions">
          {status === 'FREE' && (
            <>
              <button
                type="button"
                className="button primary"
                disabled={busy}
                onClick={() => run('auto', () => createMatch(eventId, courtNumber))}
              >
                {busyAction === 'auto' ? 'Picking…' : 'Auto-pick players'}
              </button>
              <button type="button" className="button" disabled={busy} onClick={() => setPicker('create')}>
                Choose players
              </button>
            </>
          )}

          {status === 'SCHEDULED' && (
            <>
              <button
                type="button"
                className="button primary"
                disabled={busy}
                onClick={() => run('start', () => startMatch(eventId, match.id))}
              >
                {busyAction === 'start' ? 'Starting…' : 'Start match'}
              </button>
              {/* Reshuffle only changes the "auto" seats, so it's only useful if there are some. */}
              {[...match.teamA, ...match.teamB].some((seat) => seat.autoAssigned) && (
                <button
                  type="button"
                  className="button"
                  disabled={busy}
                  onClick={() => run('reshuffle', () => reshuffleMatch(eventId, match.id))}
                >
                  {busyAction === 'reshuffle' ? 'Reshuffling…' : 'Reshuffle'}
                </button>
              )}
              <button type="button" className="button" disabled={busy} onClick={() => setPicker('lineup')}>
                Change line-up
              </button>
            </>
          )}

          {match && (
            <button type="button" className="button danger" disabled={busy} onClick={handleCancelMatch}>
              {busyAction === 'cancel' ? 'Cancelling…' : 'Cancel match'}
            </button>
          )}
        </div>
      )}
    </section>
  );
}