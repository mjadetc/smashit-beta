import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

// FRIDAY_SMASH has 2 courts.
const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
  'GET /api/events/5': { body: FRIDAY_SMASH },
};

// A seat in a match, as the backend sends it.
function seat(participantId, playerName, autoAssigned = true) {
  return { participantId, playerName, autoAssigned };
}

// A match on a court. Only the fields the Courts page uses, plus a few for realism.
function match(id, courtNumber, status, teamA, teamB, extra = {}) {
  return {
    id,
    eventId: 5,
    courtNumber,
    status,
    teamA,
    teamB,
    winningTeam: null,
    startedAt: null,
    completedAt: null,
    createdAt: '2026-11-20T11:00:00Z',
    ...extra,
  };
}

// A line of the "next up" queue.
function waiting(participantId, playerName, completedMatches = 0, lastPlayedAt = null) {
  return { participantId, playerName, completedMatches, lastPlayedAt };
}

const QUEUE = [waiting(11, 'Bea'), waiting(12, 'Carlo'), waiting(13, 'Dani'), waiting(14, 'Ella'), waiting(15, 'Finn', 1, '2026-11-20T11:40:00Z')];

// Court 1's match after "Auto-pick players": the backend chose all 4.
const AUTO_MATCH = match(41, 1, 'SCHEDULED', [seat(11, 'Bea'), seat(12, 'Carlo')], [seat(13, 'Dani'), seat(14, 'Ella')]);

// The region for one court, e.g. court(1) = the "Court 1" card.
function court(number) {
  return screen.getByRole('region', { name: `Court ${number}` });
}

describe('Courts page', () => {
  it('shows every court and who is next up', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': {
        body: [
          match(30, 1, 'COMPLETED', [seat(1, 'Ana'), seat(2, 'Leo')], [seat(3, 'Mia'), seat(4, 'Ned')]),
          match(31, 2, 'IN_PROGRESS', [seat(1, 'Ana', false), seat(2, 'Leo')], [seat(3, 'Mia'), seat(4, 'Ned')], {
            startedAt: '2026-11-20T11:30:00Z',
          }),
        ],
      },
      'GET /api/events/5/queue': { body: QUEUE },
    });

    renderApp('/events/5/courts');

    // Court 1 is free again (its only match is finished).
    expect(await screen.findByRole('heading', { name: 'Courts' })).toBeInTheDocument();
    expect(within(court(1)).getByText('Free')).toBeInTheDocument();
    expect(within(court(1)).getByText('1 played')).toBeInTheDocument();
    expect(within(court(1)).getByRole('button', { name: 'Auto-pick players' })).toBeInTheDocument();
    // Court 2 is playing. Leo was picked by the app ("auto"), Ana by the organizer.
    expect(within(court(2)).getByText('Playing')).toBeInTheDocument();
    expect(within(court(2)).getByText('Started at 7:30 PM. Live scoring is coming next.')).toBeInTheDocument();
    expect(within(court(2)).getByText('Ana')).not.toHaveTextContent('auto');
    expect(within(court(2)).getByText('Leo')).toHaveTextContent('auto');
    // Next up, in the backend's order.
    const nextUp = screen.getByRole('list', { name: 'Next up' });
    const rows = within(nextUp).getAllByRole('listitem');
    expect(rows).toHaveLength(5);
    expect(rows[0]).toHaveTextContent('Bea0 played · new');
    expect(rows[4]).toHaveTextContent('Finn1 played · last at 7:40 PM');
  });

  it('auto-picks four players for a free court', async () => {
    let queueLoads = 0;
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [] },
      // The first load shows everyone; after the match is set up, only Finn is free.
      'GET /api/events/5/queue': () => {
        queueLoads += 1;
        return { body: queueLoads === 1 ? QUEUE : [QUEUE[4]] };
      },
      'POST /api/events/5/matches': { status: 201, body: AUTO_MATCH },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await within(await screen.findByRole('region', { name: 'Court 1' })).findByRole('button', {
      name: 'Auto-pick players',
    }));

    expect(await within(court(1)).findByText('Up next')).toBeInTheDocument();
    expect(within(court(1)).getByText('Dani')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST').body).toEqual({ courtNumber: 1, teamA: [], teamB: [] });
    // The queue was loaded again and now only has Finn.
    expect(await within(screen.getByRole('list', { name: 'Next up' })).findAllByRole('listitem')).toHaveLength(1);
  });

  it('lets the organizer choose some players and auto-picks the rest', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [] },
      'GET /api/events/5/queue': { body: QUEUE },
      'POST /api/events/5/matches': {
        status: 201,
        body: match(41, 1, 'SCHEDULED', [seat(11, 'Bea', false), seat(13, 'Dani')], [seat(12, 'Carlo', false), seat(14, 'Ella')]),
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    // Both courts are free, so look inside Court 1 only.
    await user.click(await within(await screen.findByRole('region', { name: 'Court 1' })).findByRole('button', {
      name: 'Choose players',
    }));
    await user.selectOptions(screen.getByLabelText('Team A player 1'), 'Bea');
    await user.selectOptions(screen.getByLabelText('Team B player 1'), 'Carlo');
    // Bea is taken, so she can't be picked for another seat.
    expect(within(screen.getByLabelText('Team B player 2')).getByRole('option', { name: 'Bea' })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: 'Create match' }));

    expect(await within(court(1)).findByText('Up next')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST').body).toEqual({ courtNumber: 1, teamA: [11], teamB: [12] });
  });

  it('keeps the picker open and explains when there are not enough players', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [] },
      'GET /api/events/5/queue': { body: QUEUE.slice(0, 3) },
      'POST /api/events/5/matches': {
        status: 409,
        body: { detail: 'Not enough free players to fill the court: 4 needed, 3 available.' },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await within(await screen.findByRole('region', { name: 'Court 2' })).findByRole('button', {
      name: 'Choose players',
    }));
    await user.click(screen.getByRole('button', { name: 'Create match' }));

    expect(await within(court(2)).findByRole('alert')).toHaveTextContent('4 needed, 3 available.');
    expect(screen.getByRole('button', { name: 'Create match' })).toBeEnabled();
  });

  it('starts a scheduled match', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [AUTO_MATCH] },
      'GET /api/events/5/queue': { body: [] },
      'POST /api/events/5/matches/41/start': {
        body: { ...AUTO_MATCH, status: 'IN_PROGRESS', startedAt: '2026-11-20T12:05:00Z' },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await screen.findByRole('button', { name: 'Start match' }));

    expect(await within(court(1)).findByText('Playing')).toBeInTheDocument();
    expect(within(court(1)).getByText(/Started at 8:05 PM/)).toBeInTheDocument();
    expect(within(court(1)).queryByRole('button', { name: 'Start match' })).not.toBeInTheDocument();
    expect(requests.some((r) => r.url === '/api/events/5/matches/41/start')).toBe(true);
  });

  it('reshuffles the seats the app picked', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [AUTO_MATCH] },
      'GET /api/events/5/queue': { body: [QUEUE[4]] },
      'POST /api/events/5/matches/41/reshuffle': {
        body: { ...AUTO_MATCH, teamB: [seat(13, 'Dani'), seat(15, 'Finn')] },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await screen.findByRole('button', { name: 'Reshuffle' }));

    expect(await within(court(1)).findByText('Finn')).toBeInTheDocument();
    expect(within(court(1)).queryByText('Ella')).not.toBeInTheDocument();
  });

  it('changes the line-up of a scheduled match', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [AUTO_MATCH] },
      'GET /api/events/5/queue': { body: [QUEUE[4]] },
      'PUT /api/events/5/matches/41/lineup': {
        body: { ...AUTO_MATCH, teamB: [seat(13, 'Dani', false), seat(15, 'Finn', false)] },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await screen.findByRole('button', { name: 'Change line-up' }));
    // The picker starts with the current players.
    expect(screen.getByLabelText('Team A player 1')).toHaveDisplayValue('Bea');
    // Finn (waiting) replaces Ella.
    await user.selectOptions(screen.getByLabelText('Team B player 2'), 'Finn');
    await user.click(screen.getByRole('button', { name: 'Save line-up' }));

    expect(await within(court(1)).findByText('Finn')).toBeInTheDocument();
    expect(requests.find((r) => r.method === 'PUT').body).toEqual({ teamA: [11, 12], teamB: [13, 15] });
  });

  it('cancels a match after asking', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/matches': { body: [AUTO_MATCH] },
      'GET /api/events/5/queue': { body: [] },
      'POST /api/events/5/matches/41/cancel': { body: { ...AUTO_MATCH, status: 'CANCELLED' } },
    });
    const user = userEvent.setup();
    renderApp('/events/5/courts');

    await user.click(await screen.findByRole('button', { name: 'Cancel match' }));

    expect(confirm).toHaveBeenCalledOnce();
    // A cancelled match no longer occupies the court.
    expect(await within(court(1)).findByText('Free')).toBeInTheDocument();
    expect(within(court(1)).queryByText('Bea')).not.toBeInTheDocument();
  });

  it('only shows the courts of an event that is not active', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: { ...FRIDAY_SMASH, status: 'COMPLETED' } },
      'GET /api/events/5/matches': { body: [] },
      'GET /api/events/5/queue': { body: QUEUE },
    });

    renderApp('/events/5/courts');

    expect(await screen.findByRole('status')).toHaveTextContent("This event isn't active");
    expect(screen.queryByRole('button', { name: 'Auto-pick players' })).not.toBeInTheDocument();
  });

  it('is opened from the event page', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [] },
    });

    renderApp('/events/5');

    expect(await screen.findByRole('link', { name: 'Open courts' })).toHaveAttribute('href', '/events/5/courts');
  });
});