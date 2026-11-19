// Helpers for dates and times.
//
// The backend sends and receives times as ISO text with a time zone, e.g.
//   "2026-11-20T11:00:00Z"  (Z = UTC, which is 7:00 PM in the Philippines)
// An <input type="datetime-local"> works with LOCAL time and no time zone, e.g.
//   "2026-11-20T19:00"
// These functions convert between the two, using the time zone of the user's device.

// Adds a leading zero: 7 -> "07".
function twoDigits(number) {
  return String(number).padStart(2, '0');
}

// "2026-11-20T11:00:00Z" -> "2026-11-20T19:00" (on a device in the Philippines).
// Used to fill in the form when editing an event.
export function toLocalInputValue(isoText) {
  if (!isoText) return '';
  const date = new Date(isoText);
  return (
    `${date.getFullYear()}-${twoDigits(date.getMonth() + 1)}-${twoDigits(date.getDate())}` +
    `T${twoDigits(date.getHours())}:${twoDigits(date.getMinutes())}`
  );
}

// "2026-11-20T19:00" -> "2026-11-20T11:00:00.000Z". An empty input -> null,
// so the backend answers "Start time is required." like for any missing field.
export function fromLocalInputValue(localText) {
  if (!localText) return null;
  // Text without a time zone is read as LOCAL time by new Date(...).
  // toISOString() then writes it in UTC, with the "Z", which Spring understands.
  return new Date(localText).toISOString();
}

// true when both times fall on the same calendar day (in local time).
function isSameDay(a, b) {
  return a.toDateString() === b.toDateString();
}

// true when the event starts today. `now` can be passed in, so tests don't depend on the clock.
export function startsToday(isoText, now = new Date()) {
  return isSameDay(new Date(isoText), now);
}

// Intl.DateTimeFormat = the browser's built-in date formatting.
const dayFormat = new Intl.DateTimeFormat('en-US', {
  weekday: 'short',
  month: 'short',
  day: 'numeric',
  year: 'numeric',
});
const timeFormat = new Intl.DateTimeFormat('en-US', { hour: 'numeric', minute: '2-digit' });

// Newer browsers put a special narrow space before "PM". We swap it for a normal
// space, so the text is the same everywhere (and easy to check in tests).
function clean(text) {
  return text.replace(/\u202f/g, ' ');
}

// "Fri, Nov 20, 2026 · 7:00 PM – 10:00 PM"
// If it ends on another day: "Fri, Nov 20, 2026 · 9:00 PM – Sat, Nov 21, 2026 · 1:00 AM"
export function formatEventTime(startsAt, endsAt) {
  const start = new Date(startsAt);
  const end = new Date(endsAt);
  const startText = `${dayFormat.format(start)} · ${timeFormat.format(start)}`;
  const endText = isSameDay(start, end)
    ? timeFormat.format(end)
    : `${dayFormat.format(end)} · ${timeFormat.format(end)}`;
  return clean(`${startText} – ${endText}`);
}