import { rankStandings, signed } from '../events/standings.js';

// The standings: who has won the most so far.
//   stats       = the backend's /player-stats list (already sorted)
//   showGames   = true for best-of-three events. In a single-game event every match
//                 is one game, so a "games" column would just repeat W and L.
//
// A real <table> (not divs): screen readers can then say "Ana, row 1, W: 3".
// <abbr title="..."> explains the short column names when you hover or long-press.
export default function StandingsTable({ stats, showGames }) {
  const rows = rankStandings(stats);

  return (
    <section className="card section" aria-labelledby="standings-heading">
      <h2 id="standings-heading">Standings</h2>
      {/* The wrapper scrolls sideways if a long name makes the table too wide for the phone. */}
      <div className="table-scroll">
        <table className="standings">
          <thead>
            <tr>
              <th scope="col">
                <abbr title="Rank">#</abbr>
              </th>
              <th scope="col" className="player-col">
                Player
              </th>
              <th scope="col">
                <abbr title="Matches played">P</abbr>
              </th>
              <th scope="col">
                <abbr title="Wins">W</abbr>
              </th>
              <th scope="col">
                <abbr title="Losses">L</abbr>
              </th>
              {showGames && (
                <th scope="col">
                  <abbr title="Games won and lost">Games</abbr>
                </th>
              )}
              <th scope="col">
                <abbr title="Points won minus points lost">+/−</abbr>
              </th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              // The leaders (rank 1) get a yellow edge.
              <tr key={row.participantId} className={row.rank === 1 ? 'leader' : undefined}>
                <td>{row.rank ?? '–'}</td>
                {/* scope="row": this cell names the row, for screen readers. */}
                <th scope="row" className="player-col">
                  {row.playerName}
                  {/* A removed player stays listed while they have results that count. */}
                  {!row.active && <span className="muted removed-tag"> (removed)</span>}
                </th>
                <td>{row.matchesPlayed}</td>
                <td>{row.wins}</td>
                <td>{row.losses}</td>
                {showGames && (
                  <td>
                    {row.gamesWon}–{row.gamesLost}
                  </td>
                )}
                {/* title = the points themselves, shown on hover / long-press. */}
                <td title={`${row.pointsWon} won, ${row.pointsLost} lost`}>
                  {signed(row.pointsWon - row.pointsLost)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="field-hint">
        Only confirmed results count. Players who haven&apos;t finished a match yet are listed last.
      </p>
    </section>
  );
}