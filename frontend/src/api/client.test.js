import { afterEach, describe, expect, it, vi } from 'vitest';

import { fakeBackend } from '../test/fakeBackend.jsx';
import { api, ApiError, setUnauthorizedHandler } from './client.js';

// Unit tests for the api() helper. No React here: just requests and answers.
describe('api()', () => {
  afterEach(() => {
    setUnauthorizedHandler(null);
  });

  it('sends cookies, JSON and the CSRF token on a POST', async () => {
    // Pretend the backend already gave us the CSRF cookie.
    document.cookie = 'XSRF-TOKEN=token-123';
    const requests = fakeBackend({
      'POST /api/events': { status: 201, body: { id: 5, name: 'Friday Smash' } },
    });

    const event = await api('/api/events', { method: 'POST', body: { name: 'Friday Smash' } });

    expect(event).toEqual({ id: 5, name: 'Friday Smash' });
    expect(requests[0].credentials).toBe('include');
    expect(requests[0].headers['X-XSRF-TOKEN']).toBe('token-123');
    expect(requests[0].headers['Content-Type']).toBe('application/json');
    expect(requests[0].body).toEqual({ name: 'Friday Smash' });
  });

  it('does not send the CSRF token or a body on a GET', async () => {
    document.cookie = 'XSRF-TOKEN=token-123';
    const requests = fakeBackend({ 'GET /api/events': { body: [] } });

    await api('/api/events');

    expect(requests[0].headers).toEqual({});
    expect(requests[0].body).toBeUndefined();
  });

  it('returns null when there is no body (204 No Content)', async () => {
    fakeBackend({ 'DELETE /api/events/5': { status: 204 } });

    await expect(api('/api/events/5', { method: 'DELETE' })).resolves.toBeNull();
  });

  it('turns a validation error into an ApiError with field messages', async () => {
    fakeBackend({
      'POST /api/events': {
        status: 400,
        body: {
          detail: 'One or more fields are invalid.',
          errors: { name: 'Name is required.' },
        },
      },
    });

    // .rejects = the promise fails; toMatchObject checks only the fields we list.
    await expect(api('/api/events', { method: 'POST', body: {} })).rejects.toMatchObject({
      status: 400,
      message: 'One or more fields are invalid.',
      fieldErrors: { name: 'Name is required.' },
    });
  });

  it('still gives a useful error when the error has no JSON body', async () => {
    fakeBackend({ 'POST /api/events': { status: 403 } });

    const error = await api('/api/events', { method: 'POST', body: {} }).catch((e) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(403);
    expect(error.message).toBe('Something went wrong (error 403).');
    expect(error.fieldErrors).toEqual({});
  });

  it('calls the "not logged in" handler on a 401', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fakeBackend({ 'GET /api/auth/me': { status: 401 } });

    await expect(api('/api/auth/me')).rejects.toMatchObject({ status: 401 });
    expect(handler).toHaveBeenCalledOnce();
  });

  it('explains when the server cannot be reached', async () => {
    // fetch() itself fails, like when the backend isn't running.
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));

    await expect(api('/api/events')).rejects.toMatchObject({
      status: 0,
      message: "Can't reach the server. Check that it's running and try again.",
    });
  });
});