import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
  // The event page also loads the event's players; here there are none.
  'GET /api/events/5/participants': { body: [] },
};

// Pretend the person answers the browser's "Are you sure?" box with OK (true) or Cancel (false).
// vi.spyOn replaces window.confirm for this test; setup.js puts the real one back
// (vi.restoreAllMocks).
function answerConfirm(answer) {
  return vi.spyOn(window, 'confirm').mockReturnValue(answer);
}

describe('Event page', () => {
  it('shows the event details', async () => {
    fakeBackend({ ...LOGGED_IN, 'GET /api/events/5': { body: FRIDAY_SMASH } });

    renderApp('/events/5');

    expect(await screen.findByRole('heading', { name: 'Friday Night Smash' })).toBeInTheDocument();
    expect(screen.getByText('Active')).toBeInTheDocument();
    expect(screen.getByText('Fri, Nov 20, 2026 · 7:00 PM – 10:00 PM')).toBeInTheDocument();
    expect(screen.getByText('Single game to 21, change ends at 11')).toBeInTheDocument();
    expect(screen.getByText('Bring your own racket.')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Edit' })).toHaveAttribute('href', '/events/5/edit');
  });

  it('marks the event completed after asking', async () => {
    const confirm = answerConfirm(true);
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: FRIDAY_SMASH },
      'POST /api/events/5/complete': { body: { ...FRIDAY_SMASH, status: 'COMPLETED' } },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Mark completed' }));

    expect(confirm).toHaveBeenCalledOnce();
    // The page now shows the new status, and the buttons for a completed event.
    expect(await screen.findByText('Completed')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Reactivate' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Edit' })).not.toBeInTheDocument();
  });

  it('does nothing when the organizer says no', async () => {
    answerConfirm(false);
    const requests = fakeBackend({ ...LOGGED_IN, 'GET /api/events/5': { body: FRIDAY_SMASH } });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Cancel event' }));

    expect(requests.some((r) => r.method === 'POST' && r.url.startsWith('/api/events/5'))).toBe(false);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('reactivates a cancelled event without asking', async () => {
    const confirm = answerConfirm(true);
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: { ...FRIDAY_SMASH, status: 'CANCELLED' } },
      'POST /api/events/5/reactivate': { body: FRIDAY_SMASH },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Reactivate' }));

    expect(await screen.findByRole('link', { name: 'Edit' })).toBeInTheDocument();
    expect(confirm).not.toHaveBeenCalled();
  });

  it('deletes the event and goes back to My Events', async () => {
    answerConfirm(true);
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: FRIDAY_SMASH },
      'DELETE /api/events/5': { status: 204 },
      'GET /api/events?status=ACTIVE': { body: [] },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Delete' }));

    expect(await screen.findByRole('heading', { name: 'My Events' })).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'DELETE')).toBe(true);
  });

  it('shows the backend message when an action fails', async () => {
    answerConfirm(true);
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: FRIDAY_SMASH },
      'POST /api/events/5/complete': { status: 409, body: { detail: 'The event is already completed.' } },
    });
    const user = userEvent.setup();
    renderApp('/events/5');

    await user.click(await screen.findByRole('button', { name: 'Mark completed' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The event is already completed.');
    expect(screen.getByRole('button', { name: 'Mark completed' })).toBeEnabled();
  });

  it('says so when the event does not exist', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/99': { status: 404, body: { detail: 'Event not found.' } },
    });

    renderApp('/events/99');

    expect(await screen.findByRole('alert')).toHaveTextContent('Event not found.');
  });
});