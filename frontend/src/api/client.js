// The ONE place that talks to the backend. Every screen calls api(...) instead of
// fetch(...), so these rules live here only:
//   1. send the session cookie (JSESSIONID) with every request
//   2. send the CSRF token on every POST / PUT / PATCH / DELETE
//   3. turn the backend's error JSON (ProblemDetail) into an ApiError
//   4. tell the app when the session has expired (401)
//
// URLs start with "/api/...". In development, Vite forwards them to the Spring Boot
// backend on port 8080 (see "server.proxy" in vite.config.js), so the browser thinks
// the frontend and backend are the SAME site. That keeps cookies simple.

// An error from the backend, with everything a form needs to show it.
//   status      = the HTTP status (400, 401, 404, 409...), or 0 if the server couldn't be reached
//   message     = the backend's "detail" text, e.g. "Invalid email or password."
//   fieldErrors = one message per form field, e.g. { email: "Email is required." }
export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || `Something went wrong (error ${status}).`);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = problem?.errors ?? {};
  }
}

// The methods that change data. Spring Security wants a CSRF token on these.
const CHANGING_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE'];

// What to do when the backend says "you're not logged in" (401).
// AuthProvider sets this, so this file doesn't need to know about React.
let unauthorizedHandler = null;

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler;
}

// Reads one cookie's value from document.cookie, which looks like "a=1; XSRF-TOKEN=abc".
// Returns null if the cookie isn't there.
function readCookie(name) {
  const prefix = `${name}=`;
  const cookie = document.cookie
    .split('; ')
    .find((part) => part.startsWith(prefix));
  return cookie ? decodeURIComponent(cookie.slice(prefix.length)) : null;
}

// Calls the backend and returns the JSON response (or null when there is no body).
// Examples:
//   const events = await api('/api/events');
//   const event  = await api('/api/events', { method: 'POST', body: { name: 'Friday Smash' } });
// Throws an ApiError when the backend answers with an error status.
export async function api(path, { method = 'GET', body } = {}) {
  const headers = {};

  // Only say "this is JSON" when we actually send a body.
  if (body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  // The CSRF token: the backend put it in the XSRF-TOKEN cookie, and we copy it into
  // a header. Another website can make the browser SEND our cookies, but it can't READ
  // them, so it can't copy the token. That's how the backend tells real requests apart.
  if (CHANGING_METHODS.includes(method)) {
    const token = readCookie('XSRF-TOKEN');
    if (token) {
      headers['X-XSRF-TOKEN'] = token;
    }
  }

  let response;
  try {
    response = await fetch(path, {
      method,
      headers,
      // "include" = send cookies (the JSESSIONID login session) with the request.
      credentials: 'include',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // fetch() itself only fails when there's no answer at all (server down, no internet).
    throw new ApiError(0, {
      detail: "Can't reach the server. Check that it's running and try again.",
    });
  }

  // 401 = not logged in (or the session expired). Let the app go back to the login page.
  if (response.status === 401 && unauthorizedHandler) {
    unauthorizedHandler();
  }

  if (!response.ok) {
    // Most errors have a ProblemDetail JSON body, but not all (e.g. a 403 from
    // Spring Security), so reading it may fail. Then we just use the status.
    let problem = null;
    try {
      problem = await response.json();
    } catch {
      problem = null;
    }
    throw new ApiError(response.status, problem);
  }

  // 204 No Content (e.g. delete, logout) has no body. Others might be empty too.
  const text = await response.text();
  return text ? JSON.parse(text) : null;
}

// Asks the backend to (re)send the XSRF-TOKEN cookie. Called when the app starts,
// and again after logging in or out, because those start a new session.
export function loadCsrfToken() {
  return api('/api/auth/csrf');
}