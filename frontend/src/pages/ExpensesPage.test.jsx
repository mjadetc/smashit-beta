import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';

import { ANA, fakeBackend, FRIDAY_SMASH, renderApp } from '../test/fakeBackend.jsx';

const BASE = '/api/events/5';

// Players, as the backend's "sharing" list returns them.
function sharingRow(participantId, playerName, extra = {}) {
  return {
    participantId,
    playerName,
    active: true,
    playedAMatch: true,
    sharesEqually: true,
    decidedByOrganizer: false,
    ...extra,
  };
}

const PLAYERS = [
  sharingRow(1, 'Ana'),
  sharingRow(2, 'Ben'),
  sharingRow(3, 'Coach Jun', { playedAMatch: false, sharesEqually: false, decidedByOrganizer: true }),
  sharingRow(4, 'Leo', { active: false }), // left early, but played: still shares
];

const COURTS = {
  id: 9,
  eventId: 5,
  description: 'Courts 1 and 2',
  category: 'COURT_RENTAL',
  amount: 900,
  expenseDate: '2026-11-20',
  paidBy: { participantId: 2, playerName: 'Ben' },
  outsidePayerName: null,
  outsidePayerRepaid: null,
  createdAt: '2026-11-20T11:00:00Z',
};

// An expense summary as the backend returns it: ₱900 courts paid by Ben, split by 3.
function summary(overrides = {}) {
  return {
    eventId: 5,
    shareRounding: 'NONE',
    totalExpenses: 900,
    gifts: 0,
    toCover: 900,
    contributionsTotal: 0,
    sharedEqually: 900,
    sharingParticipants: 3,
    equalShare: 300,
    surplus: 0,
    surplusKeptBy: null,
    shortfall: 0,
    categories: [{ category: 'COURT_RENTAL', amount: 900 }],
    balances: [],
    settlements: [],
    receivers: [
      {
        person: { participantId: 2, playerName: 'Ben' },
        total: 600,
        payments: [
          { from: { participantId: 1, playerName: 'Ana' }, to: { participantId: 2, playerName: 'Ben' }, amount: 300 },
          { from: { participantId: 4, playerName: 'Leo' }, to: { participantId: 2, playerName: 'Ben' }, amount: 300 },
        ],
      },
    ],
    ...overrides,
  };
}

// Everything the page loads. Tests replace single answers with their own.
function backend(overrides = {}) {
  return fakeBackend({
    'GET /api/auth/csrf': { status: 204 },
    'GET /api/auth/me': { body: ANA },
    [`GET ${BASE}`]: { body: FRIDAY_SMASH },
    [`GET ${BASE}/expenses/summary`]: { body: summary() },
    [`GET ${BASE}/expenses`]: { body: [COURTS] },
    [`GET ${BASE}/contributions`]: { body: [] },
    [`GET ${BASE}/expenses/sharing`]: { body: PLAYERS },
    ...overrides,
  });
}

// The card (region) with this heading, e.g. section('Expenses').
const section = (name) => screen.getByRole('region', { name });

describe('Expenses page', () => {
  it('shows the split and who pays whom', async () => {
    // Ben paid 900 and owes his 300 share, so he gets 600 back.
    const ben = {
      person: { participantId: 2, playerName: 'Ben' },
      outsidePayer: false,
      active: true,
      sharesEqually: true,
      paid: 900,
      contribution: 0,
      share: 300,
      owes: 300,
      surplusKept: 0,
      balance: 600,
    };
    backend({ [`GET ${BASE}/expenses/summary`]: { body: summary({ balances: [ben] }) } });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    const split = await screen.findByRole('region', { name: 'The split' });
    expect(within(split).getByText('Total spent').nextSibling).toHaveTextContent('₱900.00');
    expect(within(split).getByText('₱300.00')).toBeInTheDocument();
    expect(within(split).getByText(/each, for 3 players/)).toBeInTheDocument();
    // 900 / 3 = 300 exactly, so no "a few pay ₱0.01 more" note.
    expect(within(split).queryByText(/a few pay/)).not.toBeInTheDocument();

    const settle = section('Settle up');
    expect(within(settle).getByText(/receives/)).toHaveTextContent('Ben receives ₱600.00');
    const payments = within(settle).getAllByRole('listitem').slice(1); // [0] is Ben's block
    expect(payments.map((payment) => payment.textContent)).toEqual(['from Ana₱300.00', 'from Leo₱300.00']);

    // The expense in the list, in words.
    expect(within(section('Expenses')).getByText('Court rental · Fri, Nov 20 · Paid by Ben')).toBeInTheDocument();
    // Everyone's numbers are in a <details> box: tap its summary line to open it.
    await user.click(screen.getByText("Everyone's numbers"));
    const benRow = within(screen.getByRole('table')).getByRole('row', { name: /Ben/ });
    expect(benRow).toHaveTextContent('Ben₱900.00₱300.00+₱600.00');
  });

  it('adds an expense paid by a player, then loads fresh numbers', async () => {
    let saved = false;
    const requests = backend({
      [`POST ${BASE}/expenses`]: () => {
        saved = true;
        return { status: 201, body: {} };
      },
      // After saving, the list has a second expense.
      [`GET ${BASE}/expenses`]: () => ({
        body: saved ? [COURTS, { ...COURTS, id: 10, description: 'Shuttlecocks', category: 'SHUTTLECOCKS', amount: 300 }] : [COURTS],
      }),
    });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: '+ Add expense' }));
    const form = screen.getByRole('form', { name: 'Add expense' });
    await user.type(within(form).getByLabelText('What for'), 'Shuttlecocks');
    await user.selectOptions(within(form).getByLabelText('Category'), 'Shuttlecocks');
    await user.type(within(form).getByLabelText('Amount (₱)'), '300');
    await user.clear(within(form).getByLabelText('Date'));
    await user.type(within(form).getByLabelText('Date'), '2026-11-20');
    await user.selectOptions(within(form).getByLabelText('Paid by'), 'Ana');
    // A removed player can't be chosen as a new payer.
    expect(within(within(form).getByLabelText('Paid by')).queryByText('Leo')).not.toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Add expense' }));

    expect(await within(section('Expenses')).findByText('Shuttlecocks')).toBeInTheDocument();
    expect(screen.queryByRole('form', { name: 'Add expense' })).not.toBeInTheDocument();
    expect(requests.find((r) => r.method === 'POST').body).toEqual({
      description: 'Shuttlecocks',
      category: 'SHUTTLECOCKS',
      amount: 300,
      expenseDate: '2026-11-20',
      paidById: 1,
      outsidePayerName: null,
      outsidePayerRepaid: null,
    });
    // The summary was asked for again (once when opening, once after the save).
    expect(requests.filter((r) => r.url === `${BASE}/expenses/summary`)).toHaveLength(2);
  });

  it('adds a gift from someone who is not a player', async () => {
    const requests = backend({ [`POST ${BASE}/expenses`]: { status: 201, body: {} } });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: '+ Add expense' }));
    const form = screen.getByRole('form', { name: 'Add expense' });
    await user.type(within(form).getByLabelText('What for'), 'Water');
    await user.type(within(form).getByLabelText('Amount (₱)'), '150.50');
    await user.selectOptions(within(form).getByLabelText('Paid by'), 'Someone else (not a player)');
    await user.type(within(form).getByLabelText('Their name'), 'Tita Rose');
    await user.click(within(form).getByRole('checkbox', { name: /Pay them back/ })); // untick: a gift
    expect(within(form).getByText(/It's a gift/)).toBeInTheDocument();
    await user.click(within(form).getByRole('button', { name: 'Add expense' }));

    await vi.waitFor(() => expect(requests.some((r) => r.method === 'POST')).toBe(true));
    expect(requests.find((r) => r.method === 'POST').body).toMatchObject({
      amount: 150.5,
      paidById: null,
      outsidePayerName: 'Tita Rose',
      outsidePayerRepaid: false,
    });
  });

  it('shows the backend messages under the fields', async () => {
    backend({
      [`POST ${BASE}/expenses`]: {
        status: 400,
        body: { detail: 'One or more fields are invalid.', errors: { amount: 'Amount is required.' } },
      },
    });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: '+ Add expense' }));
    await user.click(within(screen.getByRole('form', { name: 'Add expense' })).getByRole('button', { name: 'Add expense' }));

    expect(await screen.findByText('Amount is required.')).toBeInTheDocument();
    expect(screen.getByLabelText('Amount (₱)')).toHaveAttribute('aria-invalid', 'true');
  });

  it('edits an expense in its row', async () => {
    const requests = backend({ [`PUT ${BASE}/expenses/9`]: { body: COURTS } });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: 'Edit Courts 1 and 2' }));
    const form = screen.getByRole('form', { name: 'Edit expense' });
    // The form starts with the expense's values.
    expect(within(form).getByLabelText('Paid by')).toHaveDisplayValue('Ben');
    const amount = within(form).getByLabelText('Amount (₱)');
    await user.clear(amount);
    await user.type(amount, '950');
    await user.click(within(form).getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => expect(screen.queryByRole('form', { name: 'Edit expense' })).not.toBeInTheDocument());
    expect(requests.find((r) => r.method === 'PUT').body).toMatchObject({ amount: 950, paidById: 2 });
  });

  it('deletes an expense after asking', async () => {
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const requests = backend({ [`DELETE ${BASE}/expenses/9`]: { status: 204 } });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: 'Delete Courts 1 and 2' }));

    expect(confirm).toHaveBeenCalledWith('Delete "Courts 1 and 2" (₱900.00)?');
    await vi.waitFor(() => expect(requests.some((r) => r.method === 'DELETE')).toBe(true));
  });

  it('changes the rounding and shows the new share', async () => {
    const requests = backend({
      [`PUT ${BASE}/expenses/settings`]: { body: summary({ shareRounding: 'PESO_10', equalShare: 310, surplus: 30, surplusKeptBy: { participantId: 2, playerName: 'Ben' } }) },
    });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.selectOptions(await screen.findByLabelText('Round each share'), 'Up to ₱10');

    const split = section('The split');
    expect(await within(split).findByText('₱310.00')).toBeInTheDocument();
    expect(within(split).getByText(/₱30.00 more is collected than needed/)).toHaveTextContent('Ben keeps it.');
    expect(requests.find((r) => r.method === 'PUT').body).toEqual({ shareRounding: 'PESO_10' });
  });

  it('warns when nobody shares the costs', async () => {
    backend({
      [`GET ${BASE}/expenses/summary`]: { body: summary({ sharingParticipants: 0, sharedEqually: 900, equalShare: 0, shortfall: 900 }) },
    });

    renderApp('/events/5/expenses');

    expect(await screen.findByRole('alert')).toHaveTextContent("Nobody shares the costs, so ₱900.00 isn't covered.");
  });

  it('turns sharing off for a player, and back to automatic', async () => {
    const requests = backend({
      [`PUT ${BASE}/expenses/sharing/1`]: { body: {} },
      [`PUT ${BASE}/expenses/sharing/3`]: { body: {} },
    });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    const sharing = await screen.findByRole('list', { name: 'Who shares the costs' });
    expect(within(sharing).getByRole('checkbox', { name: /Leo/ })).toBeChecked();
    expect(within(sharing).getByText('Set by you')).toBeInTheDocument();

    await user.click(within(sharing).getByRole('checkbox', { name: /Ana/ }));
    await user.click(within(sharing).getByRole('button', { name: 'Use the automatic rule for Coach Jun' }));

    await vi.waitFor(() => expect(requests.filter((r) => r.method === 'PUT')).toHaveLength(2));
    const puts = requests.filter((r) => r.method === 'PUT');
    expect(puts.map((r) => [r.url, r.body])).toEqual([
      [`${BASE}/expenses/sharing/1`, { sharesEqually: false }],
      [`${BASE}/expenses/sharing/3`, { sharesEqually: null }],
    ]);
  });

  it('adds a contribution for an expense', async () => {
    const requests = backend({ [`POST ${BASE}/contributions`]: { status: 201, body: {} } });
    const user = userEvent.setup();
    renderApp('/events/5/expenses');

    await user.click(await screen.findByRole('button', { name: '+ Add contribution' }));
    const form = screen.getByRole('form', { name: 'Add contribution' });
    // Removed players can still chip in.
    await user.selectOptions(within(form).getByLabelText('Who'), 'Leo (removed)');
    await user.type(within(form).getByLabelText('Amount (₱)'), '200');
    await user.selectOptions(within(form).getByLabelText('For (optional)'), 'Courts 1 and 2');
    await user.click(within(form).getByRole('button', { name: 'Add contribution' }));

    await vi.waitFor(() => expect(requests.some((r) => r.method === 'POST')).toBe(true));
    expect(requests.find((r) => r.method === 'POST').body).toEqual({
      participantId: 4,
      amount: 200,
      expenseId: 9,
      note: '',
    });
  });

  it('starts with just the lists when nothing has been spent', async () => {
    backend({
      [`GET ${BASE}/expenses`]: { body: [] },
      [`GET ${BASE}/expenses/summary`]: { body: summary({ totalExpenses: 0, sharedEqually: 0, equalShare: 0, receivers: [], categories: [] }) },
    });

    renderApp('/events/5/expenses');

    expect(await screen.findByText(/No expenses yet/)).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: 'The split' })).not.toBeInTheDocument();
  });

  it('is opened from the event page', async () => {
    backend({ [`GET ${BASE}/participants`]: { body: [] } });

    renderApp('/events/5');

    expect(await screen.findByRole('link', { name: 'Expenses' })).toHaveAttribute('href', '/events/5/expenses');
  });
});