import { useState } from 'react';

import { teamName } from '../events/eventLabels.js';

// "Who serves first?" before each game.
//   scoreboard = the scoreboard from the backend (teams and games so far)
//   onStart    = async (servingTeam, serverId, receiverId) => ... ; throws on failure
// Rules (from the backend): in game 1 the organizer picks the serving team.
// From game 2 on, the winner of the previous game serves first.
export default function StartGameForm({ scoreboard, onStart }) {
  const { games } = scoreboard;
  const gameNumber = games.length + 1;
  // games.at(-1) = the last item of the array (or undefined for an empty array).
  const forcedTeam = games.at(-1)?.winningTeam ?? null;

  const [servingTeam, setServingTeam] = useState(forcedTeam ?? 'A');
  // The ids are kept as text, like every <select> value. '' = "the first player of the team".
  const [serverId, setServerId] = useState('');
  const [receiverId, setReceiverId] = useState('');
  const [error, setError] = useState('');
  const [starting, setStarting] = useState(false);

  const players = { A: scoreboard.teamA, B: scoreboard.teamB };
  const receivingTeam = servingTeam === 'A' ? 'B' : 'A';
  // Until a player is picked, the dropdown shows (and we send) the first one.
  const server = serverId || String(players[servingTeam][0].participantId);
  const receiver = receiverId || String(players[receivingTeam][0].participantId);

  function chooseServingTeam(team) {
    setServingTeam(team);
    // The old picks belong to the other team now: start again from the first players.
    setServerId('');
    setReceiverId('');
  }

  async function handleSubmit(submitEvent) {
    submitEvent.preventDefault();
    setError('');
    setStarting(true);
    try {
      await onStart(servingTeam, Number(server), Number(receiver));
    } catch (startError) {
      setError(startError.message);
      setStarting(false);
    }
  }

  return (
    <form className="card start-game" onSubmit={handleSubmit} noValidate>
      <h2>Game {gameNumber}: who serves first?</h2>

      {forcedTeam ? (
        <p className="muted">
          Team {forcedTeam} won game {gameNumber - 1}, so they serve first.
        </p>
      ) : (
        <fieldset className="team-choice">
          <legend>Serving team</legend>
          {['A', 'B'].map((team) => (
            <label key={team} className={servingTeam === team ? 'choice selected' : 'choice'}>
              {/* A radio button: only one in the group (same "name") can be chosen. */}
              <input
                type="radio"
                name="servingTeam"
                value={team}
                checked={servingTeam === team}
                onChange={() => chooseServingTeam(team)}
              />
              <span>
                <strong>Team {team}</strong> {teamName(players[team])}
              </span>
            </label>
          ))}
        </fieldset>
      )}

      <div className="field-row">
        <div className="field">
          <label htmlFor="first-server">Server (Team {servingTeam})</label>
          <select id="first-server" value={server} onChange={(changeEvent) => setServerId(changeEvent.target.value)}>
            {players[servingTeam].map((seat) => (
              <option key={seat.participantId} value={seat.participantId}>
                {seat.playerName}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="first-receiver">Receiver (Team {receivingTeam})</label>
          <select id="first-receiver" value={receiver} onChange={(changeEvent) => setReceiverId(changeEvent.target.value)}>
            {players[receivingTeam].map((seat) => (
              <option key={seat.participantId} value={seat.participantId}>
                {seat.playerName}
              </option>
            ))}
          </select>
        </div>
      </div>
      <p className="field-hint">Both stand in their right service court; their partners take the left.</p>

      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}

      <button type="submit" className="button primary wide" disabled={starting}>
        {starting ? 'Starting…' : `Start game ${gameNumber}`}
      </button>
    </form>
  );
}