import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';

import { ANA, fakeBackend, renderApp } from '../test/fakeBackend.jsx';

const NOT_LOGGED_IN = {
  'GET /api/auth/csrf': { status: 204 },
  'GET /api/auth/me': { status: 401 },
};

// Fills in the whole form. `overrides` changes single fields, e.g. { confirmPassword: 'x' }.
async function fillForm(user, overrides = {}) {
  const values = {
    'First name': 'Ana',
    'Last name': 'Reyes',
    Username: 'ana',
    Email: 'ana@example.com',
    'Password (at least 8 characters)': 'secret123',
    'Confirm password': 'secret123',
    ...overrides,
  };
  for (const [label, value] of Object.entries(values)) {
    await user.type(screen.getByLabelText(label), value);
  }
}

describe('Register page', () => {
  it('creates the account, logs in and opens My Events', async () => {
    const requests = fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/register': { status: 201, body: ANA },
      'POST /api/auth/login': { body: ANA },
    });
    const user = userEvent.setup();
    renderApp('/register');
    await screen.findByLabelText('First name');

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('heading', { name: 'My Events' })).toBeInTheDocument();
    const register = requests.find((r) => r.url === '/api/auth/register');
    // confirmPassword stays in the browser: it's not part of the backend's RegisterRequest.
    expect(register.body).toEqual({
      firstName: 'Ana',
      lastName: 'Reyes',
      username: 'ana',
      email: 'ana@example.com',
      password: 'secret123',
    });
    const login = requests.find((r) => r.url === '/api/auth/login');
    expect(login.body).toEqual({ email: 'ana@example.com', password: 'secret123' });
  });

  it('stops when the two passwords are different', async () => {
    const requests = fakeBackend(NOT_LOGGED_IN);
    const user = userEvent.setup();
    renderApp('/register');
    await screen.findByLabelText('First name');

    await fillForm(user, { 'Confirm password': 'something-else' });
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText("The passwords don't match.")).toBeInTheDocument();
    // Nothing was sent to the backend.
    expect(requests.some((r) => r.url === '/api/auth/register')).toBe(false);
  });

  it('shows the backend message under each field', async () => {
    fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/register': {
        status: 400,
        body: {
          detail: 'One or more fields are invalid.',
          errors: { password: 'Password must be between 8 and 72 characters.' },
        },
      },
    });
    const user = userEvent.setup();
    renderApp('/register');
    await screen.findByLabelText('First name');

    await fillForm(user, {
      'Password (at least 8 characters)': 'short',
      'Confirm password': 'short',
    });
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Password must be between 8 and 72 characters.')).toBeInTheDocument();
  });

  it('shows the message when the email is already registered', async () => {
    fakeBackend({
      ...NOT_LOGGED_IN,
      'POST /api/auth/register': {
        status: 409,
        body: { detail: 'An account with this email already exists.' },
      },
    });
    const user = userEvent.setup();
    renderApp('/register');
    await screen.findByLabelText('First name');

    await fillForm(user);
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('An account with this email already exists.');
  });
});