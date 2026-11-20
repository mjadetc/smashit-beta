import { formatPeso } from '../utils/money.js';

// The "Settle up" card: who pays whom, grouped by the person who receives the money,
// so the organizer can say "Ben, collect from these three people":
//   Ben receives ₱555.00
//     Ana  ₱185.00
//     Leo  ₱185.00 ...
// Below it, everyone's numbers in a <details> box (closed until tapped).
//   summary = the backend's expense summary (receivers and balances)
export default function SettleUpSection({ summary }) {
  return (
    <section className="card section" aria-labelledby="settle-heading">
      <h2 id="settle-heading">Settle up</h2>

      {summary.receivers.length === 0 ? (
        <p className="muted">Nobody owes anybody anything.</p>
      ) : (
        <ul className="receiver-list" aria-label="Who pays whom">
          {summary.receivers.map((receiver) => (
            // An outside payer has no participantId, so the name is the key then.
            <li key={receiver.person.participantId ?? receiver.person.playerName} className="receiver">
              <p className="receiver-title">
                <strong>{receiver.person.playerName}</strong> receives {formatPeso(receiver.total)}
              </p>
              <ul className="payment-list">
                {receiver.payments.map((payment) => (
                  <li key={payment.from.participantId ?? payment.from.playerName} className="payment">
                    <span>from {payment.from.playerName}</span>
                    <span className="money-amount">{formatPeso(payment.amount)}</span>
                  </li>
                ))}
              </ul>
            </li>
          ))}
        </ul>
      )}

      {/* <details> + <summary> = a built-in "click to show more" box, no React state needed. */}
      {summary.balances.length > 0 && (
        <details className="balances">
          <summary>Everyone&apos;s numbers</summary>
          <div className="table-scroll">
            <table className="balance-table">
              <thead>
                <tr>
                  <th scope="col" className="player-col">
                    Person
                  </th>
                  <th scope="col">Paid</th>
                  <th scope="col">Owes</th>
                  <th scope="col">Balance</th>
                </tr>
              </thead>
              <tbody>
                {summary.balances.map((line) => (
                  <tr key={line.person.participantId ?? line.person.playerName}>
                    <th scope="row" className="player-col">
                      {line.person.playerName}
                      {line.outsidePayer && <span className="muted removed-tag"> (not a player)</span>}
                    </th>
                    <td>{formatPeso(line.paid)}</td>
                    <td>{formatPeso(line.owes)}</td>
                    {/* Positive = they get money back, negative = they pay. */}
                    <td className={line.balance > 0 ? 'balance-plus' : line.balance < 0 ? 'balance-minus' : undefined}>
                      {line.balance > 0 && '+'}
                      {line.balance < 0 && '−'}
                      {formatPeso(Math.abs(line.balance))}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <p className="field-hint">
            Owes = their share plus what they chose to contribute. Balance = paid − owes (plus any extra
            they keep).
          </p>
        </details>
      )}
    </section>
  );
}