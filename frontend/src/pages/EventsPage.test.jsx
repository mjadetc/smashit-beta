import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

// The answers every test needs: Ana is logged in.
const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
};

describe('My Events page', () => {
  it('lists the active events', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events?status=ACTIVE': { body: [FRIDAY_SMASH] },
    });

    renderApp('/events');

    // The card is a link named after everything inside it, so we look inside it.
    const card = await screen.findByRole('link', { name: /Friday Night Smash/ });
    expect(card).toHaveAttribute('href', '/events/5');
    expect(within(card).getByText('Fri, Nov 20, 2026 · 7:00 PM – 10:00 PM')).toBeInTheDocument();
    expect(within(card).getByText('Court Hub, Quezon City')).toBeInTheDocument();
    expect(within(card).getByText('2 courts · Single game to 21')).toBeInTheDocument();
    // The "Active" tab is the selected one.
    expect(screen.getByRole('link', { name: 'Active' })).toHaveAttribute('aria-current', 'page');
  });

  it('invites the organizer to create a first event when there are none', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events?status=ACTIVE': { body: [] },
    });

    renderApp('/events');

    const button = await screen.findByRole('link', { name: 'Create your first event' });
    expect(button).toHaveAttribute('href', '/events/new');
  });

  it('shows completed events on the Completed tab, with their status', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events?status=ACTIVE': { body: [] },
      'GET /api/events?status=COMPLETED': { body: [{ ...FRIDAY_SMASH, status: 'COMPLETED' }] },
    });
    const user = userEvent.setup();
    renderApp('/events');

    await user.click(await screen.findByRole('link', { name: 'Completed' }));

    const card = await screen.findByRole('link', { name: /Friday Night Smash/ });
    expect(within(card).getByText('Completed')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Completed' })).toHaveAttribute('aria-current', 'page');
    expect(requests.some((r) => r.url === '/api/events?status=COMPLETED')).toBe(true);
  });

  it('shows the error when the list cannot be loaded', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events?status=ACTIVE': { status: 500, body: { detail: 'Something broke.' } },
    });

    renderApp('/events');

    expect(await screen.findByRole('alert')).toHaveTextContent('Something broke.');
  });
});