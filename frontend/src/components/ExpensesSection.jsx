import { useState } from 'react';

import { deleteExpense } from '../api/expenses.js';
import { CATEGORY_LABELS } from '../events/eventLabels.js';
import { formatDate } from '../utils/dates.js';
import { formatPeso } from '../utils/money.js';
import ExpenseForm from './ExpenseForm.jsx';

// Who paid, in words: "Paid by Ana", "Paid by Coach Jun", "Gift from Coach Jun".
function payerText(expense) {
  if (expense.paidBy) return `Paid by ${expense.paidBy.playerName}`;
  return expense.outsidePayerRepaid
    ? `Paid by ${expense.outsidePayerName}`
    : `Gift from ${expense.outsidePayerName}`;
}

// The "Expenses" card: the list, plus add / edit / delete.
//   eventId   = the event
//   expenses  = the backend's list (earliest date first)
//   players   = the sharing rows, for the "Paid by" dropdown
//   onChanged = called after any change, so the page loads fresh numbers
export default function ExpensesSection({ eventId, expenses, players, onChanged }) {
  // What the form is doing: null = closed, 'new' = adding, or the id being edited.
  // One form at a time keeps the small screen calm.
  const [editing, setEditing] = useState(null);
  const [busyId, setBusyId] = useState(null);
  const [listError, setListError] = useState('');

  function handleSaved() {
    setEditing(null);
    onChanged();
  }

  async function handleDelete(expense) {
    if (!window.confirm(`Delete "${expense.description}" (${formatPeso(expense.amount)})?`)) return;

    setBusyId(expense.id);
    setListError('');
    try {
      await deleteExpense(eventId, expense.id);
      onChanged();
    } catch (deleteError) {
      setListError(deleteError.message);
    }
    setBusyId(null);
  }

  return (
    <section className="card section" aria-labelledby="expenses-heading">
      <div className="section-header">
        <h2 id="expenses-heading">Expenses</h2>
        <span className="count-pill">{expenses.length}</span>
      </div>
      <p className="muted">What was bought, and who paid for it upfront.</p>

      {editing === 'new' ? (
        <ExpenseForm
          eventId={eventId}
          players={players}
          expense={null}
          onSaved={handleSaved}
          onCancel={() => setEditing(null)}
        />
      ) : (
        <button type="button" className="button primary wide" onClick={() => setEditing('new')}>
          + Add expense
        </button>
      )}

      {listError && (
        <p className="form-error" role="alert">
          {listError}
        </p>
      )}

      {expenses.length === 0 ? (
        <p className="muted">No expenses yet. Add the court booking, shuttlecocks, and so on.</p>
      ) : (
        <ul className="money-list" aria-label="Expenses">
          {expenses.map((expense) =>
            editing === expense.id ? (
              // The row turns into the form while it's being edited.
              <li key={expense.id} className="money-row editing">
                <ExpenseForm
                  eventId={eventId}
                  players={players}
                  expense={expense}
                  onSaved={handleSaved}
                  onCancel={() => setEditing(null)}
                />
              </li>
            ) : (
              <li key={expense.id} className="money-row">
                <div className="money-main">
                  <span className="money-title">{expense.description}</span>
                  <span className="money-amount">{formatPeso(expense.amount)}</span>
                </div>
                <p className="muted money-detail">
                  {CATEGORY_LABELS[expense.category]} · {formatDate(expense.expenseDate)} · {payerText(expense)}
                </p>
                <div className="row-actions">
                  <button
                    type="button"
                    className="button small"
                    disabled={busyId === expense.id}
                    onClick={() => setEditing(expense.id)}
                    aria-label={`Edit ${expense.description}`}
                  >
                    Edit
                  </button>
                  <button
                    type="button"
                    className="button small danger"
                    disabled={busyId === expense.id}
                    onClick={() => handleDelete(expense)}
                    aria-label={`Delete ${expense.description}`}
                  >
                    {busyId === expense.id ? 'Deleting…' : 'Delete'}
                  </button>
                </div>
              </li>
            ),
          )}
        </ul>
      )}
    </section>
  );
}