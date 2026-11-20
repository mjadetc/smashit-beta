import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
  'GET /api/events/5': { body: FRIDAY_SMASH },
};

// A player, as /api/events/5/participants returns it.
function player(id, playerName) {
  return { id, eventId: 5, playerName, active: true, createdAt: '2026-11-19T00:00:00Z' };
}

const BEA = player(11, 'Bea');
const CARLO = player(12, 'Carlo');
const DANI = player(13, 'Dani');
const ELLA = player(14, 'Ella');
const FINN = player(15, 'Finn');

// The names in the player list, top to bottom.
function listedNames() {
  const list = screen.getByRole('list', { name: 'Players' });
  // querySelector finds the name inside each row (skipping the avatar letter and the buttons).
  return within(list)
    .getAllByRole('listitem')
    .map((item) => item.querySelector('.player-name').textContent);
}

describe('Players on the event page', () => {
  it('lists the players and says how many matches they fill', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, CARLO, DANI, ELLA, FINN] },
    });

    renderApp('/events/5');

    expect(await screen.findByText('Finn')).toBeInTheDocument();
    expect(listedNames()).toEqual(['Bea', 'Carlo', 'Dani', 'Ella', 'Finn']);
    // 5 players, 2 courts: one doubles match, one person resting.
    expect(screen.getByText('Enough for 1 match at once · 1 resting')).toBeInTheDocument();
  });

  it('adds a player and keeps the box ready for the next name', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, DANI] },
      'POST /api/events/5/participants': { status: 201, body: CARLO },
    });
    const user = userEvent.setup();
    renderApp('/events/5');
    await screen.findByText('Bea');

    // "{Enter}" presses Enter, which submits the form, like on a phone keyboard.
    await user.type(screen.getByLabelText('Add a player'), 'Carlo{Enter}');

    expect(await screen.findByText('Carlo')).toBeInTheDocument();
    expect(listedNames()).toEqual(['Bea', 'Carlo', 'Dani']); // sorted, not added at the end
    expect(screen.getByLabelText('Add a player')).toHaveValue('');
    expect(screen.getByLabelText('Add a player')).toHaveFocus();
    expect(screen.getByText('Add 1 more player for a doubles match.')).toBeInTheDocument();
    const add = requests.find((r) => r.method === 'POST');
    expect(add.body).toEqual({ playerName: 'Carlo' });
  });

  it('shows the message when the name is already taken', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA] },
      'POST /api/events/5/participants': {
        status: 409,
        body: { detail: 'A participant named "Bea" is already in this event.' },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5');
    await screen.findByText('Bea');

    await user.type(screen.getByLabelText('Add a player'), 'bea{Enter}');

    expect(await screen.findByLabelText('Add a player')).toHaveAccessibleDescription(
      'A participant named "Bea" is already in this event.',
    );
    // The typed name stays, so it can be fixed.
    expect(screen.getByLabelText('Add a player')).toHaveValue('bea');
  });

  it('renames a player', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, CARLO] },
      'PUT /api/events/5/participants/11': { body: player(11, 'Bea Santos') },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Rename Bea' }));
    const box = screen.getByLabelText('New name for Bea');
    expect(box).toHaveValue('Bea');
    await user.clear(box);
    await user.type(box, 'Bea Santos');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    expect(await screen.findByText('Bea Santos')).toBeInTheDocument();
    expect(screen.queryByLabelText('New name for Bea')).not.toBeInTheDocument();
    expect(requests.find((r) => r.method === 'PUT').body).toEqual({ playerName: 'Bea Santos' });
  });

  it('keeps the rename box open with the message when renaming fails, and Escape closes it', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, CARLO] },
      'PUT /api/events/5/participants/11': {
        status: 409,
        body: { detail: 'Another participant in this event already uses the name "Carlo".' },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Rename Bea' }));
    // The old name is selected, so typing replaces it.
    await user.keyboard('Carlo{Enter}');

    expect(await screen.findByLabelText('New name for Bea')).toHaveAccessibleDescription(
      'Another participant in this event already uses the name "Carlo".',
    );

    await user.keyboard('{Escape}');
    expect(screen.queryByLabelText('New name for Bea')).not.toBeInTheDocument();
    expect(listedNames()).toEqual(['Bea', 'Carlo']);
  });

  it('removes a player after asking', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, CARLO] },
      'DELETE /api/events/5/participants/12': { status: 204 },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Remove Carlo' }));

    expect(confirm).toHaveBeenCalledOnce();
    // The list (and the sentence above it) update once the DELETE has answered.
    await screen.findByText('Add 3 more players for a doubles match.');
    expect(listedNames()).toEqual(['Bea']);
    expect(requests.some((r) => r.method === 'DELETE')).toBe(true);
  });

  it('keeps the player and explains why when they cannot be removed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5/participants': { body: [BEA, CARLO] },
      'DELETE /api/events/5/participants/12': {
        status: 409,
        body: { detail: '"Carlo" is in a match that hasn\'t finished.' },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Remove Carlo' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('"Carlo" is in a match that hasn\'t finished.');
    expect(listedNames()).toEqual(['Bea', 'Carlo']);
  });

  it('only shows the players of a completed event', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: { ...FRIDAY_SMASH, status: 'COMPLETED' } },
      'GET /api/events/5/participants': { body: [BEA, CARLO] },
    });

    renderApp('/events/5');

    expect(await screen.findByText('Carlo')).toBeInTheDocument();
    expect(screen.getByText('Reactivate the event to change its players.')).toBeInTheDocument();
    expect(screen.queryByLabelText('Add a player')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Rename Bea' })).not.toBeInTheDocument();
  });
});