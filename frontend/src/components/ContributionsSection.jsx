import { useState } from 'react';

import { createContribution, deleteContribution } from '../api/expenses.js';
import { formatPeso, parseAmount } from '../utils/money.js';
import FormField from './FormField.jsx';

// The "Contributions" card: money a player CHOOSES to put in, e.g. "Ana covers the
// shuttlecocks: ₱300". It comes off the amount that's split equally, and that player
// pays it on top of their share (or instead of one, if they don't share).
//   eventId       = the event
//   contributions = the backend's list (oldest first)
//   players       = the sharing rows: every player, also removed ones (they can still chip in)
//   expenses      = for the optional "For" dropdown
//   onChanged     = called after any change, so the page loads fresh numbers
export default function ContributionsSection({ eventId, contributions, players, expenses, onChanged }) {
  const [adding, setAdding] = useState(false);
  const [busyId, setBusyId] = useState(null);
  const [listError, setListError] = useState('');

  async function handleDelete(contribution) {
    const question = `Delete ${contribution.participant.playerName}'s contribution of ${formatPeso(contribution.amount)}?`;
    if (!window.confirm(question)) return;

    setBusyId(contribution.id);
    setListError('');
    try {
      await deleteContribution(eventId, contribution.id);
      onChanged();
    } catch (deleteError) {
      setListError(deleteError.message);
    }
    setBusyId(null);
  }

  return (
    <section className="card section" aria-labelledby="contributions-heading">
      <div className="section-header">
        <h2 id="contributions-heading">Contributions</h2>
        <span className="count-pill">{contributions.length}</span>
      </div>
      <p className="muted">
        Money a player chooses to put in. It comes off the amount everyone splits.
      </p>

      {adding ? (
        <ContributionForm
          eventId={eventId}
          players={players}
          expenses={expenses}
          onSaved={() => {
            setAdding(false);
            onChanged();
          }}
          onCancel={() => setAdding(false)}
        />
      ) : (
        <button type="button" className="button wide" onClick={() => setAdding(true)}>
          + Add contribution
        </button>
      )}

      {listError && (
        <p className="form-error" role="alert">
          {listError}
        </p>
      )}

      {contributions.length > 0 && (
        <ul className="money-list" aria-label="Contributions">
          {contributions.map((contribution) => (
            <li key={contribution.id} className="money-row">
              <div className="money-main">
                <span className="money-title">{contribution.participant.playerName}</span>
                <span className="money-amount">{formatPeso(contribution.amount)}</span>
              </div>
              {/* The optional labels: which expense it's for, and a note. */}
              {(contribution.expenseDescription || contribution.note) && (
                <p className="muted money-detail">
                  {[contribution.expenseDescription && `For ${contribution.expenseDescription}`, contribution.note]
                    .filter(Boolean)
                    .join(' · ')}
                </p>
              )}
              <div className="row-actions">
                <button
                  type="button"
                  className="button small danger"
                  disabled={busyId === contribution.id}
                  onClick={() => handleDelete(contribution)}
                  aria-label={`Delete ${contribution.participant.playerName}'s contribution`}
                >
                  {busyId === contribution.id ? 'Deleting…' : 'Delete'}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

// The small "add a contribution" form. Field names start with "contribution" so their
// ids never clash with the expense form's fields (FormField makes ids from names).
function ContributionForm({ eventId, players, expenses, onSaved, onCancel }) {
  const [values, setValues] = useState({ contributor: '', contributionAmount: '', forExpense: '', contributionNote: '' });
  const [fieldErrors, setFieldErrors] = useState({});
  const [formError, setFormError] = useState('');
  const [saving, setSaving] = useState(false);

  function handleChange(changeEvent) {
    const { name, value } = changeEvent.target;
    setValues((current) => ({ ...current, [name]: value }));
  }

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    setFieldErrors({});
    setFormError('');
    setSaving(true);
    try {
      await createContribution(eventId, {
        participantId: values.contributor === '' ? null : Number(values.contributor),
        amount: parseAmount(values.contributionAmount),
        expenseId: values.forExpense === '' ? null : Number(values.forExpense),
        note: values.contributionNote,
      });
      onSaved();
    } catch (error) {
      setFieldErrors(error.fieldErrors);
      if (Object.keys(error.fieldErrors).length === 0) {
        setFormError(error.message);
      }
      setSaving(false);
    }
  }

  return (
    <form className="money-form" onSubmit={handleSubmit} noValidate aria-label="Add contribution">
      <div className="field-row">
        <FormField
          label="Who"
          name="contributor"
          as="select"
          value={values.contributor}
          onChange={handleChange}
          // The backend names its fields participantId / amount / note.
          error={fieldErrors.participantId}
        >
          <option value="">Choose…</option>
          {players.map((player) => (
            <option key={player.participantId} value={player.participantId}>
              {player.playerName}
              {!player.active && ' (removed)'}
            </option>
          ))}
        </FormField>
        <FormField
          label="Amount (₱)"
          name="contributionAmount"
          type="number"
          inputMode="decimal"
          min="0.01"
          step="0.01"
          value={values.contributionAmount}
          onChange={handleChange}
          error={fieldErrors.amount}
        />
      </div>
      <div className="field-row">
        <FormField label="For (optional)" name="forExpense" as="select" value={values.forExpense} onChange={handleChange}>
          <option value="">Nothing in particular</option>
          {expenses.map((expense) => (
            <option key={expense.id} value={expense.id}>
              {expense.description}
            </option>
          ))}
        </FormField>
        <FormField
          label="Note (optional)"
          name="contributionNote"
          value={values.contributionNote}
          onChange={handleChange}
          error={fieldErrors.note}
          maxLength={255}
        />
      </div>

      {formError && (
        <p className="form-error" role="alert">
          {formError}
        </p>
      )}

      <div className="actions">
        <button type="submit" className="button primary" disabled={saving}>
          {saving ? 'Saving…' : 'Add contribution'}
        </button>
        <button type="button" className="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  );
}