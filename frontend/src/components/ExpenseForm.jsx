import { useState } from 'react';

import { createExpense, updateExpense } from '../api/expenses.js';
import { CATEGORY_LABELS } from '../events/eventLabels.js';
import { todayDate } from '../utils/dates.js';
import { parseAmount } from '../utils/money.js';
import FormField from './FormField.jsx';

// The value of the "Paid by" dropdown that means "someone who isn't a player".
const OUTSIDE = 'outside';

// The form's starting values: an empty form, or the expense being edited.
// Every value is TEXT, because that's what inputs and dropdowns hold.
function initialValues(expense) {
  if (!expense) {
    return {
      description: '',
      category: 'COURT_RENTAL',
      amount: '',
      expenseDate: todayDate(),
      payer: '', // nobody chosen yet
      outsidePayerName: '',
      repaid: true,
    };
  }
  return {
    description: expense.description,
    category: expense.category,
    amount: String(expense.amount),
    expenseDate: expense.expenseDate,
    payer: expense.paidBy ? String(expense.paidBy.participantId) : OUTSIDE,
    outsidePayerName: expense.outsidePayerName ?? '',
    repaid: expense.outsidePayerRepaid ?? true,
  };
}

// Add or edit one expense.
//   eventId  = the event
//   players  = the "sharing" rows (every player, with `active`): who can be the payer
//   expense  = the expense to edit, or null to add a new one
//   onSaved  = called after the backend saved it
//   onCancel = called by the Cancel button
export default function ExpenseForm({ eventId, players, expense, onSaved, onCancel }) {
  const [values, setValues] = useState(() => initialValues(expense));
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  // Only active players can be chosen as the payer... but when editing, the payer
  // who has left since must stay in the list (the backend allows keeping them).
  const payerOptions = players.filter(
    (player) => player.active || player.participantId === expense?.paidBy?.participantId,
  );
  const outside = values.payer === OUTSIDE;

  // One handler for every field: `name` says which value to change.
  // A checkbox's value is in `checked`, not in `value`.
  function handleChange(changeEvent) {
    const { name, type, value, checked } = changeEvent.target;
    setValues((current) => ({ ...current, [name]: type === 'checkbox' ? checked : value }));
  }

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    setFieldErrors({});
    setFormError('');
    setSaving(true);

    // Exactly one payer goes to the backend: a player's id OR an outside name.
    const body = {
      description: values.description,
      category: values.category,
      amount: parseAmount(values.amount),
      expenseDate: values.expenseDate || null,
      paidById: outside || values.payer === '' ? null : Number(values.payer),
      outsidePayerName: outside ? values.outsidePayerName : null,
      outsidePayerRepaid: outside ? values.repaid : null,
    };

    try {
      if (expense) {
        await updateExpense(eventId, expense.id, body);
      } else {
        await createExpense(eventId, body);
      }
      onSaved();
    } catch (error) {
      // Field messages under the fields; anything else (e.g. "Choose who paid...") on top.
      setFieldErrors(error.fieldErrors);
      if (Object.keys(error.fieldErrors).length === 0) {
        setFormError(error.message);
      }
      setSaving(false);
    }
  }

  return (
    // aria-label: the form can be found by name ("Add expense" / "Edit expense").
    <form
      className="money-form"
      onSubmit={handleSubmit}
      noValidate
      aria-label={expense ? 'Edit expense' : 'Add expense'}
    >
      <FormField
        label="What for"
        name="description"
        value={values.description}
        onChange={handleChange}
        error={fieldErrors.description}
        placeholder="e.g. Court 1 and 2, 7–10 PM"
        maxLength={255}
      />

      <div className="field-row">
        <FormField
          label="Category"
          name="category"
          as="select"
          value={values.category}
          onChange={handleChange}
          error={fieldErrors.category}
        >
          {/* Object.entries turns { COURT_RENTAL: 'Court rental' } into [['COURT_RENTAL', 'Court rental']]. */}
          {Object.entries(CATEGORY_LABELS).map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </FormField>
        <FormField
          label="Amount (₱)"
          name="amount"
          type="number"
          inputMode="decimal" // phones show a number keyboard with a "."
          min="0.01"
          step="0.01"
          value={values.amount}
          onChange={handleChange}
          error={fieldErrors.amount}
        />
      </div>

      <div className="field-row">
        <FormField
          label="Date"
          name="expenseDate"
          type="date"
          value={values.expenseDate}
          onChange={handleChange}
          error={fieldErrors.expenseDate}
        />
        <FormField label="Paid by" name="payer" as="select" value={values.payer} onChange={handleChange}>
          <option value="">Choose…</option>
          {payerOptions.map((player) => (
            <option key={player.participantId} value={player.participantId}>
              {player.playerName}
            </option>
          ))}
          <option value={OUTSIDE}>Someone else (not a player)</option>
        </FormField>
      </div>

      {/* Only for an outside payer: their name, and whether they want the money back. */}
      {outside && (
        <>
          <FormField
            label="Their name"
            name="outsidePayerName"
            value={values.outsidePayerName}
            onChange={handleChange}
            error={fieldErrors.outsidePayerName}
            placeholder="e.g. Coach Jun"
            maxLength={80}
          />
          <label className="check-field">
            <input type="checkbox" name="repaid" checked={values.repaid} onChange={handleChange} />
            <span>
              Pay them back
              <span className="field-hint">
                {values.repaid
                  ? 'The players pay this person back.'
                  : "It's a gift: it comes off the total, and nobody pays it back."}
              </span>
            </span>
          </label>
        </>
      )}

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="actions">
        <button type="submit" className="button primary" disabled={saving}>
          {saving ? 'Saving…' : expense ? 'Save changes' : 'Add expense'}
        </button>
        <button type="button" className="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  );
}