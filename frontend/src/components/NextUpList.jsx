import { formatTime } from '../utils/dates.js';

// The "Next up" card: free players, fairest first, as the backend sorts them
// (fewest matches played, then longest waiting). The first four are highlighted:
// they're who "Auto-pick players" will most likely choose.
//   queue = [{ participantId, playerName, completedMatches, lastPlayedAt }]
export default function NextUpList({ queue }) {
  return (
    <section className="card section" aria-labelledby="next-up-heading">
      <div className="section-header">
        <h2 id="next-up-heading">Next up</h2>
        <span className="count-pill">{queue.length}</span>
      </div>

      {queue.length === 0 ? (
        <p className="muted">Everyone is on a court. Add more players on the event page.</p>
      ) : (
        // <ol> = an ordered (numbered) list, because the order matters here.
        <ol className="queue-list" aria-label="Next up">
          {queue.map((entry, position) => (
            <li key={entry.participantId} className={position < 4 ? 'queue-row next' : 'queue-row'}>
              <span className="queue-position" aria-hidden="true">
                {position + 1}
              </span>
              <span className="player-name">{entry.playerName}</span>
              <span className="muted queue-detail">
                {entry.completedMatches === 1 ? '1 played' : `${entry.completedMatches} played`}
                {/* lastPlayedAt is null until they finish a match. */}
                {entry.lastPlayedAt ? ` · last at ${formatTime(entry.lastPlayedAt)}` : ' · new'}
              </span>
            </li>
          ))}
        </ol>
      )}
    </section>
  );
}