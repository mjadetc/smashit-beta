import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
};

const SUMMARY_URL = 'GET /api/events/5/summary';
const STATS_URL = 'GET /api/events/5/player-stats';

// Player refs, as the backend puts them in results.
const ANA_P = { participantId: 1, playerName: 'Ana' };
const LEO = { participantId: 2, playerName: 'Leo' };
const MIA = { participantId: 3, playerName: 'Mia' };
const NED = { participantId: 4, playerName: 'Ned' };

// One standings line. Tests give the record; the rest has sensible defaults.
function statsLine(player, wins, losses, extra = {}) {
  return {
    ...player,
    active: true,
    matchesPlayed: wins + losses,
    wins,
    losses,
    gamesWon: wins,
    gamesLost: losses,
    pointsWon: 0,
    pointsLost: 0,
    ...extra,
  };
}

// A summary as the backend returns it, with one finished match on court 1.
function summary(overrides = {}) {
  return {
    eventId: 5,
    eventName: 'Friday Night Smash',
    status: 'ACTIVE',
    matchFormat: 'SINGLE_GAME',
    activeParticipants: 4,
    scheduledMatches: 0,
    inProgressMatches: 1,
    completedMatches: 1,
    cancelledMatches: 0,
    gamesPlayed: 1,
    ralliesPlayed: 39,
    averageMatchDurationSeconds: 2040,
    longestMatchDurationSeconds: 2040,
    courts: [
      { courtNumber: 1, completedMatches: 1, currentMatchId: null, currentMatchStatus: null },
      { courtNumber: 2, completedMatches: 0, currentMatchId: 41, currentMatchStatus: 'IN_PROGRESS' },
    ],
    results: [
      {
        matchId: 40,
        courtNumber: 1,
        teamA: [ANA_P, LEO],
        teamB: [MIA, NED],
        winningTeam: 'B', // Mia & Ned won, so their score comes first: "21–18"
        games: [{ gameNumber: 1, teamAScore: 18, teamBScore: 21, status: 'COMPLETED', winningTeam: 'B' }],
        durationSeconds: 2040,
        completedAt: '2026-11-20T11:52:00Z', // 7:52 PM in Manila
      },
    ],
    ...overrides,
  };
}

const STANDINGS = [
  statsLine(MIA, 1, 0, { pointsWon: 21, pointsLost: 18 }),
  statsLine(NED, 1, 0, { pointsWon: 21, pointsLost: 18 }),
  // A 0-0 player: the backend sorts them above the losers; the page moves them down.
  statsLine({ participantId: 5, playerName: 'Bea' }, 0, 0),
  statsLine(ANA_P, 0, 1, { pointsWon: 18, pointsLost: 21 }),
  statsLine(LEO, 0, 1, { pointsWon: 18, pointsLost: 21, active: false }),
];

// The standings rows as text, e.g. ["1 Mia 1 1 0 +3", ...]. Skips the header row.
function standingsRows() {
  const table = screen.getByRole('table');
  return within(table)
    .getAllByRole('row')
    .slice(1)
    // row.cells = the row's <th> and <td> cells, left to right (a built-in of table rows).
    .map((row) => Array.from(row.cells, (cell) => cell.textContent).join(' '));
}

describe('Stats page', () => {
  it('shows the standings, with shared ranks and unplayed players last', async () => {
    fakeBackend({ ...LOGGED_IN, [SUMMARY_URL]: { body: summary() }, [STATS_URL]: { body: STANDINGS } });

    renderApp('/events/5/stats');

    expect(await screen.findByRole('heading', { name: 'Standings' })).toBeInTheDocument();
    expect(standingsRows()).toEqual([
      '1 Mia 1 1 0 +3',
      '1 Ned 1 1 0 +3',
      '3 Ana 1 0 1 \u22123',
      '3 Leo (removed) 1 0 1 \u22123',
      '– Bea 0 0 0 0',
    ]);
    // Single game: no "Games" column.
    expect(screen.queryByRole('columnheader', { name: 'Games' })).not.toBeInTheDocument();
  });

  it('adds a games column and a games tile for best of three', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [SUMMARY_URL]: { body: summary({ matchFormat: 'BEST_OF_THREE', gamesPlayed: 3 }) },
      [STATS_URL]: { body: [statsLine(MIA, 1, 0, { gamesWon: 2, gamesLost: 1 })] },
    });

    renderApp('/events/5/stats');

    expect(await screen.findByRole('columnheader', { name: 'Games' })).toBeInTheDocument();
    expect(standingsRows()).toEqual(['1 Mia 1 1 0 2–1 0']);
    expect(screen.getByText('Games', { selector: 'dt' }).nextSibling).toHaveTextContent('3');
  });

  it('shows the totals at a glance', async () => {
    fakeBackend({ ...LOGGED_IN, [SUMMARY_URL]: { body: summary() }, [STATS_URL]: { body: STANDINGS } });

    renderApp('/events/5/stats');

    const glance = await screen.findByRole('region', { name: 'At a glance' });
    // Each tile is a <dt> name followed by its <dd> value.
    const tile = (name) => within(glance).getByText(name, { selector: 'dt' }).nextSibling;
    expect(tile('Matches')).toHaveTextContent('1');
    expect(tile('Rallies')).toHaveTextContent('39');
    expect(tile('Average match')).toHaveTextContent('34 min');
    expect(tile('Longest match')).toHaveTextContent('34 min');
    expect(within(glance).getByText('Other matches: 1 playing now.')).toBeInTheDocument();
    expect(within(glance).getByText('Per court: Court 1: 1 · Court 2: 0')).toBeInTheDocument();
  });

  it('lists the results with the winners first', async () => {
    fakeBackend({ ...LOGGED_IN, [SUMMARY_URL]: { body: summary() }, [STATS_URL]: { body: STANDINGS } });

    renderApp('/events/5/stats');

    const results = await screen.findByRole('list', { name: 'Results' });
    const [first] = within(results).getAllByRole('listitem');
    expect(first).toHaveTextContent('Mia & Ned beat Ana & Leo');
    expect(first).toHaveTextContent('21–18 · 34 min · Court 1 · 7:52 PM');
  });

  it('explains what to do when no match has finished yet', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [SUMMARY_URL]: { body: summary({ completedMatches: 0, results: [], averageMatchDurationSeconds: null, longestMatchDurationSeconds: null }) },
      [STATS_URL]: { body: [statsLine(MIA, 0, 0)] },
    });

    renderApp('/events/5/stats');

    expect(await screen.findByText(/No results yet/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open courts' })).toHaveAttribute('href', '/events/5/courts');
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('loads the newest numbers on Refresh', async () => {
    let completed = 1;
    fakeBackend({
      ...LOGGED_IN,
      // A function answer: it's asked again on every request, so it can change.
      [SUMMARY_URL]: () => ({ body: summary({ completedMatches: completed }) }),
      [STATS_URL]: { body: STANDINGS },
    });
    const user = userEvent.setup();
    renderApp('/events/5/stats');

    const glance = await screen.findByRole('region', { name: 'At a glance' });
    const matchesTile = () => within(glance).getByText('Matches', { selector: 'dt' }).nextSibling;
    expect(matchesTile()).toHaveTextContent('1');

    completed = 2; // meanwhile, another match finished on a court
    await user.click(screen.getByRole('button', { name: 'Refresh' }));

    expect(await within(glance).findByText('2', { selector: 'dd' })).toBeInTheDocument();
  });

  it('shows the backend message for an unknown event', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [SUMMARY_URL]: { status: 404, body: { detail: 'Event not found.' } },
      [STATS_URL]: { status: 404, body: { detail: 'Event not found.' } },
    });

    renderApp('/events/5/stats');

    expect(await screen.findByRole('alert')).toHaveTextContent('Event not found.');
  });

  it('is opened from the event page', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: FRIDAY_SMASH },
      'GET /api/events/5/participants': { body: [] },
    });

    renderApp('/events/5');

    expect(await screen.findByRole('link', { name: 'Stats' })).toHaveAttribute('href', '/events/5/stats');
  });
});