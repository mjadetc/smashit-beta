import { useState } from 'react';

// The four seats of a doubles match, in order: Team A player 1 and 2, then Team B.
const SEATS = [
  { team: 'A', index: 0 },
  { team: 'A', index: 1 },
  { team: 'B', index: 0 },
  { team: 'B', index: 1 },
];

// Choose who plays. Every seat is a dropdown: a player, or "Auto-pick" (the default),
// which lets the backend choose the fairest free player for that seat.
//   players      = who can be chosen: [{ participantId, playerName }]
//   initialTeamA = ids already in Team A (when changing a line-up), same for B
//   onSubmit     = async (teamA, teamB) => ... ; if it throws, the message is shown
//   submitLabel  = e.g. "Create match" / "Save line-up"
//   onCancel     = close the picker
export default function LineupPicker({
  players,
  initialTeamA = [],
  initialTeamB = [],
  onSubmit,
  submitLabel,
  onCancel,
}) {
  // One value per seat, as TEXT (that's what a <select> holds): an id, or '' for auto-pick.
  const [chosen, setChosen] = useState(() =>
    SEATS.map(({ team, index }) => {
      const id = (team === 'A' ? initialTeamA : initialTeamB)[index];
      return id == null ? '' : String(id);
    }),
  );
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  function handleChange(seatNumber, value) {
    // .map makes a NEW array (React needs a new value to notice the change).
    setChosen((current) => current.map((old, i) => (i === seatNumber ? value : old)));
  }

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    // Keep only the seats with a player, and turn the text back into numbers.
    const teamA = chosen.slice(0, 2).filter(Boolean).map(Number);
    const teamB = chosen.slice(2).filter(Boolean).map(Number);

    setError('');
    setSaving(true);
    try {
      await onSubmit(teamA, teamB);
      // On success the court card closes the picker, so there's nothing else to do.
    } catch (submitError) {
      // e.g. "Not enough free players to fill the court: 4 needed, 3 available."
      setError(submitError.message);
      setSaving(false);
    }
  }

  return (
    <form className="lineup-picker" onSubmit={handleSubmit} noValidate>
      <div className="lineup-teams">
        {['A', 'B'].map((team) => (
          // <fieldset> groups related inputs; its <legend> is the group's title.
          <fieldset key={team} className="lineup-team">
            <legend>Team {team}</legend>
            {SEATS.map(({ team: seatTeam, index }, seatNumber) => {
              if (seatTeam !== team) return null;
              const selectId = `seat-${team}-${index}`;
              return (
                <div key={selectId} className="field">
                  <label htmlFor={selectId} className="visually-hidden">
                    Team {team} player {index + 1}
                  </label>
                  <select
                    id={selectId}
                    value={chosen[seatNumber]}
                    onChange={(changeEvent) => handleChange(seatNumber, changeEvent.target.value)}
                  >
                    <option value="">Auto-pick</option>
                    {players.map((player) => {
                      const value = String(player.participantId);
                      // Someone already in ANOTHER seat can't be picked again.
                      const takenElsewhere = chosen.some((id, i) => i !== seatNumber && id === value);
                      return (
                        <option key={value} value={value} disabled={takenElsewhere}>
                          {player.playerName}
                        </option>
                      );
                    })}
                  </select>
                </div>
              );
            })}
          </fieldset>
        ))}
      </div>

      <p className="field-hint">Seats left on Auto-pick get the players who have waited longest.</p>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <div className="actions">
        <button type="submit" className="button primary" disabled={saving}>
          {saving ? 'Saving…' : submitLabel}
        </button>
        <button type="button" className="button" onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  );
}