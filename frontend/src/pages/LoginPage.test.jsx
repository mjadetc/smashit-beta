import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, renderApp } from '../test/fakeBackend.jsx';

// The answers every test needs when the app opens and nobody is logged in yet.
const NOT_LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { status: 401 },
  // My Events loads the active events after logging in; here there are none.
  'GET /api/events?status=ACTIVE': { body: [] },
};

// Fills in the login form and presses the button, like a real person would.
async function logIn(user, email, password) {
  await user.type(screen.getByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('Login page', () => {
  it('sends someone who is not logged in to the login page', async () => {
    fakeBackend(NOT_LOGGED_IN);

    renderApp('/events');

    // findBy... waits for it to appear (the app first asks the backend who is logged in).
    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument();
  });

  it('logs in and opens My Events', async () => {
    const requests = fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/login': { body: ANA },
    });
    const user = userEvent.setup();
    renderApp('/login');
    await screen.findByLabelText('Email');

    await logIn(user, 'ana@example.com', 'secret123');

    expect(await screen.findByRole('heading', { name: 'My Events' })).toBeInTheDocument();
    // The header shows who is logged in.
    expect(screen.getByText('Ana')).toBeInTheDocument();
    const login = requests.find((r) => r.url === '/api/auth/login');
    expect(login.body).toEqual({ email: 'ana@example.com', password: 'secret123' });
  });

  it('shows the message for a wrong password', async () => {
    fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/login': { status: 401, body: { detail: 'Invalid email or password.' } },
    });
    const user = userEvent.setup();
    renderApp('/login');
    await screen.findByLabelText('Email');

    await logIn(user, 'ana@example.com', 'wrong-password');

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.');
    // Still on the login page, and the button works again.
    expect(screen.getByRole('button', { name: 'Log in' })).toBeEnabled();
  });

  it('shows the backend message under each field', async () => {
    fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/login': {
        status: 400,
        body: {
          detail: 'One or more fields are invalid.',
          errors: { email: 'Email is required.', password: 'Password is required.' },
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/login');

    await user.click(await screen.findByRole('button', { name: 'Log in' }));

    expect(await screen.findByText('Email is required.')).toBeInTheDocument();
    expect(screen.getByText('Password is required.')).toBeInTheDocument();
    // The input is marked invalid, and points at its message for screen readers.
    expect(screen.getByLabelText('Email')).toHaveAccessibleDescription('Email is required.');
  });

  it('goes back to the page they wanted after logging in', async () => {
    fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/login': { body: ANA },
    });
    const user = userEvent.setup();
    // They tried to open /events, got sent to /login...
    renderApp('/events');
    await screen.findByLabelText('Email');

    await logIn(user, 'ana@example.com', 'secret123');

    // ...and land on /events again.
    expect(await screen.findByRole('heading', { name: 'My Events' })).toBeInTheDocument();
  });

  it('skips the login page when already logged in', async () => {
    fakeBackend({
      'GET /api/auth/csrf': { status: 204 },
      'GET /api/auth/me': { body: ANA },
      // My Events loads the active events after logging in; here there are none.
      'GET /api/events?status=ACTIVE': { body: [] },
    });

    

    renderApp('/login');

    expect(await screen.findByRole('heading', { name: 'My Events' })).toBeInTheDocument();
  });

  it('logs out and goes back to the login page', async () => {
    const requests = fakeBackend({
      'GET /api/auth/csrf': { status: 204 },
      'GET /api/auth/me': { body: ANA },
      // My Events loads the active events after logging in; here there are none.
      'GET /api/events?status=ACTIVE': { body: [] },
      'POST /api/auth/logout': { status: 204 },
    });
    const user = userEvent.setup();
    renderApp('/events');

    await user.click(await screen.findByRole('button', { name: 'Log out' }));

    expect(await screen.findByRole('button', { name: 'Log in' })).toBeInTheDocument();
    expect(requests.some((r) => r.method === 'POST' && r.url === '/api/auth/logout')).toBe(true);
  });
});