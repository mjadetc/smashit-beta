import { teamName } from '../events/eventLabels.js';
import { scoreLine } from '../events/standings.js';
import { formatDuration, formatTime } from '../utils/dates.js';

// The finished matches, most recent first (the backend sorts them):
//   Ana & Leo beat Mia & Ned
//   21–18 · 34 min · Court 1 · 9:52 PM
//   results = summary.results: [{ matchId, courtNumber, teamA, teamB, winningTeam,
//                                 games, durationSeconds, completedAt }]
export default function ResultsList({ results }) {
  return (
    <section className="card section" aria-labelledby="results-heading">
      <div className="section-header">
        <h2 id="results-heading">Results</h2>
        <span className="count-pill">{results.length}</span>
      </div>
      <ol className="result-list" aria-label="Results">
        {results.map((result) => {
          const winners = result.winningTeam === 'A' ? result.teamA : result.teamB;
          const losers = result.winningTeam === 'A' ? result.teamB : result.teamA;
          return (
            <li key={result.matchId} className="result-row">
              <p className="result-teams">
                <strong>{teamName(winners)}</strong> beat {teamName(losers)}
              </p>
              <p className="muted result-detail">
                {scoreLine(result.games, result.winningTeam)} · {formatDuration(result.durationSeconds)} · Court{' '}
                {result.courtNumber} · {formatTime(result.completedAt)}
              </p>
            </li>
          );
        })}
      </ol>
    </section>
  );
}