import { useState } from 'react';

import { updateRounding } from '../api/expenses.js';
import { CATEGORY_LABELS, ROUNDING_LABELS } from '../events/eventLabels.js';
import { formatPeso } from '../utils/money.js';

// The top card: how the total becomes "each player pays ₱X".
//   total spent - gifts - contributions = split equally, then / number of sharers
//   summary   = the backend's expense summary
//   onSummary = gets the NEW summary after the rounding changes
export default function MoneySummary({ eventId, summary, onSummary }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  async function handleRounding(changeEvent) {
    setSaving(true);
    setError('');
    try {
      // This call answers with the whole new summary: no need to load anything else.
      onSummary(await updateRounding(eventId, changeEvent.target.value));
    } catch (roundingError) {
      setError(roundingError.message);
    }
    setSaving(false);
  }

  const sharers = summary.sharingParticipants;
  // Does the exact split leave a few centavos over? (Compared in centavos, as whole
  // numbers, because 0.1 + 0.2 isn't exactly 0.3 in JavaScript.)
  const toCents = (amount) => Math.round(amount * 100);
  const uneven = toCents(summary.equalShare) * sharers !== toCents(summary.sharedEqually);

  return (
    <section className="card section" aria-labelledby="money-heading">
      <h2 id="money-heading">The split</h2>

      {/* The sum, line by line. Lines that are 0 are left out. */}
      <dl className="money-sum">
        <dt>Total spent</dt>
        <dd>{formatPeso(summary.totalExpenses)}</dd>
        {summary.gifts > 0 && (
          <>
            <dt>Gifts</dt>
            <dd>−{formatPeso(summary.gifts)}</dd>
          </>
        )}
        {summary.contributionsTotal > 0 && (
          <>
            <dt>Contributions</dt>
            <dd>−{formatPeso(summary.contributionsTotal)}</dd>
          </>
        )}
        <dt className="money-sum-total">Split equally</dt>
        <dd className="money-sum-total">{formatPeso(summary.sharedEqually)}</dd>
      </dl>

      {sharers > 0 && summary.sharedEqually > 0 && (
        <p className="equal-share">
          <span className="equal-share-amount">{formatPeso(summary.equalShare)}</span>
          <span className="muted">
            each, for {sharers === 1 ? '1 player' : `${sharers} players`}
            {/* An exact split can't always be equal to the centavo: 100 / 3 = 33.33, and
                one person pays 33.34. Rounding up never has this problem. */}
            {summary.shareRounding === 'NONE' && uneven && ' (a few pay ₱0.01 more)'}
          </span>
        </p>
      )}

      {/* Money nobody pays for: only possible when nobody shares. */}
      {summary.shortfall > 0 && (
        <p className="form-error" role="alert">
          Nobody shares the costs, so {formatPeso(summary.shortfall)} isn&apos;t covered. Tick at least one
          player under &quot;Who shares the costs?&quot;.
        </p>
      )}

      {/* Rounding up (or generous contributions) can collect a bit more than needed. */}
      {summary.surplus > 0 && summary.surplusKeptBy && (
        <p className="notice">
          {formatPeso(summary.surplus)} more is collected than needed (rounding or contributions).{' '}
          {summary.surplusKeptBy.playerName} keeps it.
        </p>
      )}

      <div className="field">
        <label htmlFor="share-rounding">Round each share</label>
        <select id="share-rounding" value={summary.shareRounding} onChange={handleRounding} disabled={saving}>
          {Object.entries(ROUNDING_LABELS).map(([code, label]) => (
            <option key={code} value={code}>
              {label}
            </option>
          ))}
        </select>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      {summary.categories.length > 0 && (
        <p className="muted">
          {summary.categories
            .map((category) => `${CATEGORY_LABELS[category.category]} ${formatPeso(category.amount)}`)
            .join(' · ')}
        </p>
      )}
    </section>
  );
}