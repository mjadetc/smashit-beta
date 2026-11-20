import { describe, expect, it } from 'vitest';

import { rankStandings, scoreLine, signed } from './standings.js';

// A standings line with only the fields rankStandings looks at.
const line = (playerName, wins, losses) => ({
  playerName,
  wins,
  losses,
  matchesPlayed: wins + losses,
  pointsWon: 0,
  pointsLost: 0,
});

describe('rankStandings', () => {
  it('numbers the players, sharing a rank when the record is the same', () => {
    const ranked = rankStandings([line('Ana', 3, 0), line('Leo', 2, 1), line('Mia', 2, 1), line('Ned', 0, 3)]);

    expect(ranked.map((player) => player.rank)).toEqual([1, 2, 2, 4]);
  });

  it('lists the better point difference first among players with the same record', () => {
    const ranked = rankStandings([
      { ...line('Dani', 1, 1), pointsWon: 30, pointsLost: 35 },
      { ...line('Faye', 1, 1), pointsWon: 40, pointsLost: 35 },
    ]);

    expect(ranked.map((player) => [player.playerName, player.rank])).toEqual([
      ['Faye', 1],
      ['Dani', 1],
    ]);
  });

  it('puts players who have not played yet at the bottom, without a rank', () => {
    const ranked = rankStandings([line('Ana', 1, 0), line('Bea', 0, 0), line('Ned', 0, 1)]);

    expect(ranked.map((player) => [player.playerName, player.rank])).toEqual([
      ['Ana', 1],
      ['Ned', 2],
      ['Bea', null],
    ]);
  });
});

describe('signed', () => {
  it('shows the sign of a difference', () => {
    expect(signed(12)).toBe('+12');
    expect(signed(-3)).toBe('\u22123');
    expect(signed(0)).toBe('0');
  });
});

describe('scoreLine', () => {
  const games = [
    { teamAScore: 18, teamBScore: 21 },
    { teamAScore: 21, teamBScore: 19 },
    { teamAScore: 15, teamBScore: 21 },
  ];

  it("writes the winner's score first", () => {
    expect(scoreLine(games, 'B')).toBe('21–18, 19–21, 21–15');
    expect(scoreLine(games, 'A')).toBe('18–21, 21–19, 15–21');
  });
});