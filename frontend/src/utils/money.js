// Money helpers.
//
// The backend sends amounts as JSON numbers with 2 decimals, e.g. 1234.50 (JavaScript
// reads it as 1234.5). We never do money maths here: the backend does it in centavos.
// The frontend only SHOWS amounts, and sends what the organizer typed.

// The browser's built-in currency formatting, for Philippine pesos.
const pesoFormat = new Intl.NumberFormat('en-PH', { style: 'currency', currency: 'PHP' });

// 1234.5 -> "₱1,234.50"
export function formatPeso(amount) {
  return pesoFormat.format(amount);
}

// What the organizer typed in an amount box -> what we send.
//   "250.50" -> 250.5     "" -> null (the backend then says "Amount is required.")
export function parseAmount(text) {
  return text.trim() === '' ? null : Number(text);
}