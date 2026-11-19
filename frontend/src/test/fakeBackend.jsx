import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { vi } from 'vitest';

import App from '../App.jsx';

// A pretend backend for tests: replaces fetch() with a function that answers from a list.
//   const requests = fakeBackend({
//     'GET /api/auth/me': { status: 401 },
//     'POST /api/auth/login': { status: 200, body: { firstName: 'Ana' } },
//   });
// An answer can also be a function, to answer differently each time it's called.
// Returns the list of requests that were made, so tests can check what was sent.
export function fakeBackend(answers) {
  const requests = [];

  vi.stubGlobal(
    'fetch',
    vi.fn(async (url, options = {}) => {
      const method = options.method ?? 'GET';
      const request = {
        method,
        url,
        headers: options.headers ?? {},
        credentials: options.credentials,
        body: options.body ? JSON.parse(options.body) : undefined,
      };
      requests.push(request);

      const answer = answers[`${method} ${url}`];
      if (!answer) {
        return jsonResponse(404, { detail: `The fake backend has no answer for ${method} ${url}` });
      }
      const { status = 200, body } = typeof answer === 'function' ? answer(request) : answer;
      return jsonResponse(status, body);
    }),
  );

  return requests;
}

// A real Response object, like the one fetch() gives back.
function jsonResponse(status, body) {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

// Draws the whole app as if the browser had opened `url`.
export function renderApp(url) {
  return render(
    <MemoryRouter initialEntries={[url]}>
      <App />
    </MemoryRouter>,
  );
}

// A logged-in organizer, as /api/auth/me and /api/auth/login return it.
export const ANA = {
  id: 1,
  firstName: 'Ana',
  lastName: 'Reyes',
  username: 'ana',
  email: 'ana@example.com',
  createdAt: '2026-11-19T00:00:00Z',
};