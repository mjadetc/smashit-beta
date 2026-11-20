import { describe, expect, it } from 'vitest';

import { formatPeso, parseAmount } from './money.js';

describe('money', () => {
  it('shows amounts in pesos', () => {
    expect(formatPeso(1234.5)).toBe('₱1,234.50');
    expect(formatPeso(0)).toBe('₱0.00');
  });

  it('turns a typed amount into a number, or null when empty', () => {
    expect(parseAmount('250.50')).toBe(250.5);
    expect(parseAmount('  ')).toBeNull();
  });
});