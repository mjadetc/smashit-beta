import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

const LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { body: ANA },
  // The event page also loads the event's players; here there are none.
  'GET /api/events/5/participants': { body: [] },
};

// Types the basic details into the empty "New event" form.
// (user.type on a datetime-local input needs the exact "YYYY-MM-DDTHH:MM" text.)
async function fillBasics(user) {
  await user.type(screen.getByLabelText('Event name'), 'Friday Night Smash');
  await user.type(screen.getByLabelText('Location'), 'Court Hub, Quezon City');
  await user.type(screen.getByLabelText('Starts'), '2026-11-20T19:00');
  await user.type(screen.getByLabelText('Ends'), '2026-11-20T22:00');
  // The courts box starts at "1": clear it first, then type.
  await user.clear(screen.getByLabelText('Number of courts'));
  await user.type(screen.getByLabelText('Number of courts'), '2');
}

describe('New event page', () => {
  it('creates the event and opens its page', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'POST /api/events': { status: 201, body: FRIDAY_SMASH },
      'GET /api/events/5': { body: FRIDAY_SMASH },
    });
    const user = userEvent.setup();
    renderApp('/events/new');
    await screen.findByLabelText('Event name');

    await fillBasics(user);
    await user.click(screen.getByRole('button', { name: 'Create event' }));

    // We're on the new event's page.
    expect(await screen.findByRole('heading', { name: 'Friday Night Smash' })).toBeInTheDocument();
    const create = requests.find((r) => r.method === 'POST' && r.url === '/api/events');
    // Numbers are numbers, times are UTC (7 PM in Manila = 11:00 UTC), empty description = null.
    expect(create.body).toEqual({
      name: 'Friday Night Smash',
      location: 'Court Hub, Quezon City',
      startsAt: '2026-11-20T11:00:00.000Z',
      endsAt: '2026-11-20T14:00:00.000Z',
      courtCount: 2,
      matchFormat: 'SINGLE_GAME',
      changeEndsAt: 11,
      description: null,
    });
  });

  it('hides "change ends" for best of three, and sends null for it', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'POST /api/events': { status: 201, body: { ...FRIDAY_SMASH, matchFormat: 'BEST_OF_THREE' } },
      'GET /api/events/5': { body: FRIDAY_SMASH },
    });
    const user = userEvent.setup();
    renderApp('/events/new');
    await screen.findByLabelText('Event name');

    await fillBasics(user);
    // selectOptions picks an option in a dropdown, by its visible text.
    await user.selectOptions(screen.getByLabelText('Match format'), 'Best of three games');

    expect(screen.queryByLabelText('Change ends at (points)')).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Create event' }));
    await screen.findByRole('heading', { name: 'Friday Night Smash' });
    const create = requests.find((r) => r.method === 'POST');
    expect(create.body.matchFormat).toBe('BEST_OF_THREE');
    expect(create.body.changeEndsAt).toBeNull();
  });

  it('shows the backend messages for missing fields', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'POST /api/events': {
        status: 400,
        body: {
          detail: 'One or more fields are invalid.',
          errors: { name: 'Event name is required.', startsAt: 'Start time is required.' },
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/new');

    await user.click(await screen.findByRole('button', { name: 'Create event' }));

    expect(await screen.findByText('Event name is required.')).toBeInTheDocument();
    expect(screen.getByLabelText('Starts')).toHaveAccessibleDescription('Start time is required.');
  });

  it('shows a rule about two fields as one message', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'POST /api/events': { status: 400, body: { detail: 'End time must be after the start time.' } },
    });
    const user = userEvent.setup();
    renderApp('/events/new');
    await screen.findByLabelText('Event name');

    await fillBasics(user);
    await user.click(screen.getByRole('button', { name: 'Create event' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('End time must be after the start time.');
    expect(screen.getByRole('button', { name: 'Create event' })).toBeEnabled();
  });
});

describe('Edit event page', () => {
  it('fills in the form and saves the changes', async () => {
    const requests = fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: FRIDAY_SMASH },
      'PUT /api/events/5': { body: { ...FRIDAY_SMASH, courtCount: 3 } },
    });
    const user = userEvent.setup();
    renderApp('/events/5/edit');

    // The saved values are shown, in local time.
    expect(await screen.findByLabelText('Event name')).toHaveValue('Friday Night Smash');
    expect(screen.getByLabelText('Starts')).toHaveValue('2026-11-20T19:00');
    expect(screen.getByLabelText('Change ends at (points)')).toHaveValue(11);

    await user.clear(screen.getByLabelText('Number of courts'));
    await user.type(screen.getByLabelText('Number of courts'), '3');
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    // Back on the event page.
    expect(await screen.findByRole('heading', { name: 'Friday Night Smash' })).toBeInTheDocument();
    const save = requests.find((r) => r.method === 'PUT');
    expect(save.body).toEqual({
      name: 'Friday Night Smash',
      location: 'Court Hub, Quezon City',
      startsAt: '2026-11-20T11:00:00.000Z',
      endsAt: '2026-11-20T14:00:00.000Z',
      courtCount: 3,
      matchFormat: 'SINGLE_GAME',
      changeEndsAt: 11,
      description: 'Bring your own racket.',
    });
  });

  it('does not offer the form for a completed event', async () => {
    fakeBackend({
      ...LOGGED_IN,
      'GET /api/events/5': { body: { ...FRIDAY_SMASH, status: 'COMPLETED' } },
    });

    renderApp('/events/5/edit');

    expect(await screen.findByRole('alert')).toHaveTextContent('Only active events can be edited.');
    expect(screen.queryByLabelText('Event name')).not.toBeInTheDocument();
  });
});