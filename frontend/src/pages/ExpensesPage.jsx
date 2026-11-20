import { Link, useParams } from 'react-router';

import { eventPath } from '../api/events.js';
import { contributionsPath, expenseSummaryPath, expensesPath, sharingPath } from '../api/expenses.js';
import ContributionsSection from '../components/ContributionsSection.jsx';
import ExpensesSection from '../components/ExpensesSection.jsx';
import MoneySummary from '../components/MoneySummary.jsx';
import SettleUpSection from '../components/SettleUpSection.jsx';
import SharingSection from '../components/SharingSection.jsx';
import { useApiData } from '../hooks/useApiData.js';

// /events/5/expenses: the money side of an event.
//   1. The split       - total, gifts, contributions -> what each player pays
//   2. Settle up       - who pays whom
//   3. Expenses        - what was bought, and who paid it upfront
//   4. Contributions   - money a player chooses to put in
//   5. Who shares      - which players split the costs
// Works for an event in ANY status: people often settle up after the session.
export default function ExpensesPage() {
  const { eventId } = useParams();

  const eventData = useApiData(eventPath(eventId));
  const summaryData = useApiData(expenseSummaryPath(eventId));
  const expensesData = useApiData(expensesPath(eventId));
  const contributionsData = useApiData(contributionsPath(eventId));
  const sharingData = useApiData(sharingPath(eventId));
  const all = [eventData, summaryData, expensesData, contributionsData, sharingData];

  // After ANY change, ask the backend for everything again. One change can move every
  // number (a new expense changes each share and every balance), and the maths lives
  // in the backend, so we don't try to work it out here. reload() keeps the old numbers
  // on screen until the new ones arrive.
  function refreshAll() {
    summaryData.reload();
    expensesData.reload();
    contributionsData.reload();
    sharingData.reload();
  }

  // .find() = the first error, if any.
  const error = all.find((data) => data.error)?.error;
  // .some() = true if at least one is still loading.
  const loading = all.some((data) => data.loading);

  if (error) {
    return (
      <section>
        <Link to={`/events/${eventId}`} className="back-link">
          ← Back to the event
        </Link>
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  if (loading) return <p className="page-status">Loading expenses…</p>;

  const event = eventData.data;
  const summary = summaryData.data;
  const expenses = expensesData.data;
  const contributions = contributionsData.data;
  const players = sharingData.data;
  // Nothing to split yet: the first two cards would only show zeros.
  const hasMoney = expenses.length > 0 || contributions.length > 0;

  return (
    <section>
      <Link to={`/events/${eventId}`} className="back-link">
        ← {event.name}
      </Link>
      <h1>Expenses</h1>

      {hasMoney && (
        <>
          {/* Changing the rounding returns the new summary, so it's swapped in directly. */}
          <MoneySummary eventId={eventId} summary={summary} onSummary={summaryData.setData} />
          <SettleUpSection summary={summary} />
        </>
      )}

      <ExpensesSection eventId={eventId} expenses={expenses} players={players} onChanged={refreshAll} />
      <ContributionsSection
        eventId={eventId}
        contributions={contributions}
        players={players}
        expenses={expenses}
        onChanged={refreshAll}
      />
      <SharingSection eventId={eventId} players={players} onChanged={refreshAll} />
    </section>
  );
}