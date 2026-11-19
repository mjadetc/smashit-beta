import { describe, expect, it } from 'vitest';

import { formatEventTime, fromLocalInputValue, startsToday, toLocalInputValue } from './dates.js';

// These run in the Asia/Manila time zone (UTC+8), see "test.env" in vite.config.js.
describe('dates', () => {
  it('turns a backend time into a local value for the form', () => {
    expect(toLocalInputValue('2026-11-20T11:00:00Z')).toBe('2026-11-20T19:00');
    expect(toLocalInputValue(null)).toBe('');
  });

  it('turns a form value into a UTC time for the backend', () => {
    expect(fromLocalInputValue('2026-11-20T19:00')).toBe('2026-11-20T11:00:00.000Z');
    // Empty input -> null, so the backend reports the missing field.
    expect(fromLocalInputValue('')).toBeNull();
  });

  it('shows the day once when the event starts and ends on the same day', () => {
    expect(formatEventTime('2026-11-20T11:00:00Z', '2026-11-20T14:00:00Z')).toBe(
      'Fri, Nov 20, 2026 · 7:00 PM – 10:00 PM',
    );
  });

  it('shows both days when the event goes past midnight', () => {
    expect(formatEventTime('2026-11-20T13:00:00Z', '2026-11-20T17:00:00Z')).toBe(
      'Fri, Nov 20, 2026 · 9:00 PM – Sat, Nov 21, 2026 · 1:00 AM',
    );
  });

  it('knows whether an event starts today', () => {
    const now = new Date('2026-11-20T02:00:00Z'); // 10:00 AM on Nov 20 in Manila
    expect(startsToday('2026-11-20T11:00:00Z', now)).toBe(true);
    expect(startsToday('2026-11-21T11:00:00Z', now)).toBe(false);
  });
});