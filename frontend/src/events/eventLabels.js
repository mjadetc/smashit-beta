// The backend uses codes like "SINGLE_GAME"; people should read "Single game to 21".
// Keeping the wording here means every screen uses the same words.

export const MATCH_FORMAT_LABELS = {
  SINGLE_GAME: 'Single game to 21',
  BEST_OF_THREE: 'Best of three games',
};

export const STATUS_LABELS = {
  ACTIVE: 'Active',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

// "1 court", "2 courts".
export function courtsLabel(count) {
  return count === 1 ? '1 court' : `${count} courts`;
}


// A team's names, e.g. "Ana & Leo". `players` = seats or player refs (they all have playerName).
export function teamName(players) {
  return players.map((player) => player.playerName).join(' & ');
}