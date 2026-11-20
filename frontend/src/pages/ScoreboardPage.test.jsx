import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, renderApp } from '../test/fakeBackend.jsx';

const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
};

// The match's backend address (every scoring call starts with it).
const MATCH_URL = '/api/events/5/matches/40';

// The four players: Ana & Leo (team A) against Mia & Ned (team B).
const ANA_P = { participantId: 1, playerName: 'Ana' };
const LEO = { participantId: 2, playerName: 'Leo' };
const MIA = { participantId: 3, playerName: 'Mia' };
const NED = { participantId: 4, playerName: 'Ned' };

// A seat in the line-up (a player plus "autoAssigned").
const seat = (player) => ({ ...player, autoAssigned: true });

// A scoreboard as the backend returns it. Tests override only what they need.
function board(overrides = {}) {
  return {
    matchId: 40,
    eventId: 5,
    courtNumber: 1,
    matchFormat: 'SINGLE_GAME',
    status: 'IN_PROGRESS',
    teamA: [seat(ANA_P), seat(LEO)],
    teamB: [seat(MIA), seat(NED)],
    games: [],
    currentGame: null,
    matchWinner: null,
    nextStep: 'START_GAME',
    startedAt: '2026-11-20T11:00:00Z',
    completedAt: null,
    durationSeconds: null,
    ...overrides,
  };
}

// The game being played. Ana serves from the right court to Mia, unless overridden.
function liveGame(overrides = {}) {
  return {
    gameNumber: 1,
    teamAScore: 0,
    teamBScore: 0,
    status: 'IN_PROGRESS',
    winningTeam: null,
    servingTeam: 'A',
    server: ANA_P,
    receiver: MIA,
    teamARightCourt: ANA_P,
    teamALeftCourt: LEO,
    teamBRightCourt: MIA,
    teamBLeftCourt: NED,
    endsChanged: false,
    changeEndsNow: false,
    ralliesPlayed: 0,
    ...overrides,
  };
}

// A scoreboard in the middle of game 1, with the given score.
function playing(teamAScore, teamBScore, gameOverrides = {}) {
  return board({
    nextStep: 'RECORD_POINT',
    games: [{ gameNumber: 1, teamAScore, teamBScore, status: 'IN_PROGRESS', winningTeam: null }],
    currentGame: liveGame({ teamAScore, teamBScore, ...gameOverrides }),
  });
}

describe('Court screen', () => {
  it('starts a scheduled match, then asks who serves', async () => {
    let started = false;
    const requests = fakeBackend({
      ...LOGGED_IN,
      // Before "Start match" the match is scheduled; afterwards it's in progress.
      [`GET ${MATCH_URL}/scoreboard`]: () => ({
        body: started ? board() : board({ status: 'SCHEDULED', nextStep: 'START_MATCH', startedAt: null }),
      }),
      [`POST ${MATCH_URL}/start`]: () => {
        started = true;
        return { body: {} };
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    expect(await screen.findByText(/Ana & Leo/)).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Start match' }));

    expect(await screen.findByRole('heading', { name: 'Game 1: who serves first?' })).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST' && r.url === `${MATCH_URL}/start`)).toBe(true);
  });

  it('starts game 1 with the chosen server and receiver', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: board() },
      [`POST ${MATCH_URL}/games`]: {
        body: playing(0, 0, { servingTeam: 'B', server: NED, receiver: LEO, teamBRightCourt: NED, teamBLeftCourt: MIA, teamARightCourt: LEO, teamALeftCourt: ANA_P }),
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    // A radio button is found by its label text.
    await user.click(await screen.findByRole('radio', { name: /Team B/ }));
    await user.selectOptions(screen.getByLabelText('Server (Team B)'), 'Ned');
    await user.selectOptions(screen.getByLabelText('Receiver (Team A)'), 'Leo');
    await user.click(screen.getByRole('button', { name: 'Start game 1' }));

    expect(await screen.findByRole('img', { name: 'Ned serves to Leo' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === `${MATCH_URL}/games`).body).toEqual({
      servingTeam: 'B',
      serverId: 4,
      receiverId: 2,
    });
  });

  it('lets the winner of game 1 serve first in game 2', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: {
        body: board({
          matchFormat: 'BEST_OF_THREE',
          games: [{ gameNumber: 1, teamAScore: 18, teamBScore: 21, status: 'COMPLETED', winningTeam: 'B' }],
          currentGame: liveGame({ teamAScore: 18, teamBScore: 21, status: 'COMPLETED', winningTeam: 'B' }),
        }),
      },
    });

    renderApp('/events/5/matches/40');

    expect(await screen.findByText('Team B won game 1, so they serve first.')).toBeInTheDocument();
    expect(screen.queryByRole('radio')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Server (Team B)')).toHaveDisplayValue('Mia');
  });

  it('records a rally for the team that was tapped', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: playing(5, 3) },
      [`POST ${MATCH_URL}/points`]: { body: playing(5, 4, { servingTeam: 'B', server: MIA, receiver: ANA_P }) },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    const teamB = await screen.findByRole('button', { name: 'Point to Team B, Mia & Ned' });
    expect(within(teamB).getByText('3')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Ana serves to Mia' })).toBeInTheDocument();

    await user.click(teamB);

    expect(await within(teamB).findByText('4')).toBeInTheDocument();
    // Team B won the rally, so they serve now.
    expect(within(teamB).getByText('Serving')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Mia serves to Ana' })).toBeInTheDocument();
    expect(requests.find((r) => r.url === `${MATCH_URL}/points`).body).toEqual({ team: 'B' });
  });

  it('says when to change ends, and draws the teams swapped', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: playing(11, 7, { changeEndsNow: true, endsChanged: true }) },
    });

    renderApp('/events/5/matches/40');

    expect(await screen.findByRole('alert')).toHaveTextContent('Change ends!');
    // Team B is now at the top end of the picture.
    const halves = screen.getAllByText(/^Team [AB]$/, { selector: '.court-half-label' });
    expect(halves.map((half) => half.textContent)).toEqual(['Team B', 'Team A']);
  });

  it('undoes the last rally', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: playing(5, 4) },
      [`POST ${MATCH_URL}/undo`]: { body: playing(5, 3) },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    await user.click(await screen.findByRole('button', { name: '↶ Undo last' }));

    const teamB = screen.getByRole('button', { name: 'Point to Team B, Mia & Ned' });
    expect(await within(teamB).findByText('3')).toBeInTheDocument();
    expect(requests.some((r) => r.url === `${MATCH_URL}/undo`)).toBe(true);
  });

  it('shows the backend message when a tap fails', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: playing(5, 4) },
      [`POST ${MATCH_URL}/points`]: { status: 409, body: { detail: 'Matches can only be changed while the event is active.' } },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    await user.click(await screen.findByRole('button', { name: 'Point to Team A, Ana & Leo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Matches can only be changed while the event is active.');
    expect(screen.getByRole('button', { name: 'Point to Team A, Ana & Leo' })).toBeEnabled();
  });

  it('confirms the winner and shows the final result', async () => {
    const finalGames = [{ gameNumber: 1, teamAScore: 21, teamBScore: 18, status: 'COMPLETED', winningTeam: 'A' }];
    const finished = { games: finalGames, currentGame: liveGame({ teamAScore: 21, teamBScore: 18, status: 'COMPLETED', winningTeam: 'A' }), matchWinner: 'A' };
    const requests = fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: board({ ...finished, nextStep: 'COMPLETE_MATCH' }) },
      [`POST ${MATCH_URL}/complete`]: {
        body: board({ ...finished, nextStep: 'NONE', status: 'COMPLETED', completedAt: '2026-11-20T11:34:00Z', durationSeconds: 2040 }),
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/matches/40');

    expect(await screen.findByText(/Team A wins!/)).toBeInTheDocument();
    // Undo is still possible, in case the last rally was a mistake.
    expect(screen.getByRole('button', { name: '↶ Undo last' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Confirm result' }));

    expect(await screen.findByText(/Team A won/)).toBeInTheDocument();
    expect(screen.getByText('Ana & Leo beat Mia & Ned')).toBeInTheDocument();
    expect(screen.getByText('21–18 · 34 min')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to courts' })).toHaveAttribute('href', '/events/5/courts');
    expect(screen.queryByRole('button', { name: '↶ Undo last' })).not.toBeInTheDocument();
    expect(requests.some((r) => r.url === `${MATCH_URL}/complete`)).toBe(true);
  });

  it('says so for a cancelled match', async () => {
    fakeBackend({
      ...LOGGED_IN,
      [`GET ${MATCH_URL}/scoreboard`]: { body: board({ status: 'CANCELLED', nextStep: 'NONE' }) },
    });

    renderApp('/events/5/matches/40');

    expect(await screen.findByText(/This match was cancelled/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '↶ Undo last' })).not.toBeInTheDocument();
  });
});