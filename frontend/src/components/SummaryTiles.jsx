import { formatDuration } from '../utils/dates.js';

// The event at a glance: a few big numbers in tiles.
//   summary = the backend's /summary answer
//
// It's a <dl> (description list): each tile is a <dt> name and its <dd> value,
// which is exactly what a "label: number" pair is.
export default function SummaryTiles({ summary }) {
  // Durations are null until a match finishes; the page only shows this card after one has.
  const tiles = [
    { label: 'Matches', value: summary.completedMatches },
    // Only interesting for best of three: in a single-game event it equals the matches.
    summary.matchFormat === 'BEST_OF_THREE' && { label: 'Games', value: summary.gamesPlayed },
    { label: 'Rallies', value: summary.ralliesPlayed },
    { label: 'Average match', value: formatDuration(summary.averageMatchDurationSeconds) },
    { label: 'Longest match', value: formatDuration(summary.longestMatchDurationSeconds) },
  ].filter(Boolean); // drops the `false` left by "Games" in a single-game event

  // What's still going on, e.g. "1 playing now · 1 cancelled". Zeros are left out.
  const others = [
    [summary.inProgressMatches, 'playing now'],
    [summary.scheduledMatches, 'about to start'],
    [summary.cancelledMatches, 'cancelled'],
  ]
    .filter(([count]) => count > 0)
    .map(([count, text]) => `${count} ${text}`);

  return (
    <section className="card section" aria-labelledby="summary-heading">
      <h2 id="summary-heading">At a glance</h2>
      <dl className="stat-tiles">
        {tiles.map((tile) => (
          <div key={tile.label} className="stat-tile">
            <dt>{tile.label}</dt>
            <dd>{tile.value}</dd>
          </div>
        ))}
      </dl>
      {others.length > 0 && <p className="muted">Other matches: {others.join(' · ')}.</p>}
      {/* Matches finished per court, e.g. "Court 1: 5 · Court 2: 3". */}
      <p className="muted">
        Per court: {summary.courts.map((court) => `Court ${court.courtNumber}: ${court.completedMatches}`).join(' · ')}
      </p>
    </section>
  );
}