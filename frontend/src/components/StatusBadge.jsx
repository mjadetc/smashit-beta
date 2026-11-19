import { STATUS_LABELS } from '../events/eventLabels.js';

// A small rounded label: "Active" (teal), "Completed" (grey) or "Cancelled" (red).
// The CSS class is the status in lower case: .badge.active, .badge.completed...
export default function StatusBadge({ status }) {
  return <span className={`badge ${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>;
}