import { Link, useParams } from 'react-router';

import { playerStatsPath, summaryPath } from '../api/stats.js';
import ResultsList from '../components/ResultsList.jsx';
import StandingsTable from '../components/StandingsTable.jsx';
import SummaryTiles from '../components/SummaryTiles.jsx';
import { useApiData } from '../hooks/useApiData.js';

// /events/5/stats: the standings, the event at a glance, and the results.
// Everything is read-only, so it also works for completed or cancelled events.
export default function StatsPage() {
  const { eventId } = useParams();

  // Two things to load, at the same time (like the Courts page).
  const summaryData = useApiData(summaryPath(eventId));
  const statsData = useApiData(playerStatsPath(eventId));

  const error = summaryData.error ?? statsData.error;
  const loading = summaryData.loading || statsData.loading;

  if (error) {
    return (
      <section>
        <Link to={`/events/${eventId}`} className="back-link">
          ← Back to the event
        </Link>
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  if (loading) return <p className="page-status">Loading stats…</p>;

  const summary = summaryData.data;
  const stats = statsData.data;

  return (
    <section>
      <Link to={`/events/${eventId}`} className="back-link">
        ← {summary.eventName}
      </Link>
      <div className="page-header">
        <h1>Stats</h1>
        {/* Matches keep finishing on the courts; this fetches the newest numbers.
            reload() keeps the old numbers on screen while it loads. */}
        <button
          type="button"
          className="button"
          onClick={() => {
            summaryData.reload();
            statsData.reload();
          }}
        >
          Refresh
        </button>
      </div>

      {summary.completedMatches === 0 ? (
        // Nothing to count yet: say how to get some numbers instead of showing zeros.
        <div className="card empty-state">
          <p>No results yet. Stats appear once a match result is confirmed on the court screen.</p>
          {summary.status === 'ACTIVE' && (
            <Link to={`/events/${eventId}/courts`} className="button primary">
              Open courts
            </Link>
          )}
        </div>
      ) : (
        <>
          <StandingsTable stats={stats} showGames={summary.matchFormat === 'BEST_OF_THREE'} />
          <SummaryTiles summary={summary} />
          <ResultsList results={summary.results} />
        </>
      )}
    </section>
  );
}