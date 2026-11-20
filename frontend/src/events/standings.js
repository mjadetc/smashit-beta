// Small helpers for the standings table. They're plain functions (no React), so
// they're easy to test on their own: data in, data out.

// Points won minus points lost: how convincingly a player won (or lost).
function pointDifference(line) {
  return line.pointsWon - line.pointsLost;
}

// Adds a `rank` to each line of the standings.
//   stats = the backend's list, already sorted: most wins, then fewest losses, then A to Z.
// - Players with the same wins AND losses share a rank, like a sports table: 1, 2, 2, 4.
//   Within such a group, the better point difference is listed first (it doesn't change the rank).
// - Players who haven't finished a match yet go to the bottom with rank null ("–").
//   (The backend puts a 0-0 player above a 0-3 player, but 0-0 means "hasn't played".)
export function rankStandings(stats) {
  // filter() keeps the original order, so both groups stay sorted.
  // toSorted() returns a sorted COPY (sort() would change the array React gave us).
  // The compare function returns a negative number when `a` goes first, positive when `b` does,
  // and 0 to keep the backend's order (sorting in JavaScript is "stable").
  const played = stats
    .filter((line) => line.matchesPlayed > 0)
    .toSorted(
      (a, b) =>
        b.wins - a.wins || a.losses - b.losses || pointDifference(b) - pointDifference(a),
    );
  const notPlayed = stats.filter((line) => line.matchesPlayed === 0);

  const ranked = [];
  played.forEach((line, index) => {
    const previous = ranked[index - 1]; // undefined for the first player
    const sameRecord = previous && previous.wins === line.wins && previous.losses === line.losses;
    // Same record as the player above -> same rank. Otherwise their place in the list.
    ranked.push({ ...line, rank: sameRecord ? previous.rank : index + 1 });
  });

  return [...ranked, ...notPlayed.map((line) => ({ ...line, rank: null }))];
}

// A number with its sign, for a difference: 12 -> "+12", -3 -> "−3", 0 -> "0".
// (\u2212 "−" is the real minus sign: the same width as "+", so columns line up.)
export function signed(number) {
  if (number > 0) return `+${number}`;
  if (number < 0) return `\u2212${Math.abs(number)}`;
  return '0';
}

// The games of a match as the WINNER sees them, e.g. "21–18, 19–21, 21–15".
// Writing the winner's score first reads naturally after "Ana & Leo beat Mia & Ned".
export function scoreLine(games, winningTeam) {
  return games
    .map((game) =>
      winningTeam === 'B'
        ? `${game.teamBScore}–${game.teamAScore}`
        : `${game.teamAScore}–${game.teamBScore}`,
    )
    .join(', ');
}