import { useState } from 'react';
import { Link, useParams } from 'react-router';

import { startMatch } from '../api/matches.js';
import { completeMatch, recordPoint, scoreboardPath, startGame, undoLast } from '../api/scoring.js';
import CourtDiagram from '../components/CourtDiagram.jsx';
import StartGameForm from '../components/StartGameForm.jsx';
import { teamName } from '../events/eventLabels.js';
import { useApiData } from '../hooks/useApiData.js';
import { formatDuration } from '../utils/dates.js';

// /events/5/matches/40: the court screen. The organizer (or a helper) holds the phone
// at the court and taps the team that won each rally.
//
// The backend decides what comes next and tells us in scoreboard.nextStep:
//   START_MATCH    -> a "Start match" button
//   START_GAME     -> "who serves first?"
//   RECORD_POINT   -> two big buttons, one per team, plus the court picture
//   COMPLETE_MATCH -> "Team A wins!" with "Confirm result"
//   NONE           -> the final result (or "cancelled")
// So this page never repeats the badminton rules; it just shows what the backend says.
export default function ScoreboardPage() {
  const { eventId, matchId } = useParams();
  const { data: board, loading, error, setData, reload } = useApiData(scoreboardPath(eventId, matchId));

  // Which action is running ('point-A', 'undo'...), or null. All buttons wait meanwhile,
  // so a double tap can't record the same rally twice.
  const [busyAction, setBusyAction] = useState(null);
  const [actionError, setActionError] = useState('');

  // Runs one backend call; every scoring call returns the new scoreboard.
  async function run(name, change) {
    setBusyAction(name);
    setActionError('');
    try {
      setData(await change());
    } catch (changeError) {
      setActionError(changeError.message);
    }
    setBusyAction(null);
  }

  // "Start match" returns a match, not a scoreboard, so we then load the scoreboard again.
  async function handleStartMatch() {
    setBusyAction('start-match');
    setActionError('');
    try {
      await startMatch(eventId, matchId);
      reload();
    } catch (startError) {
      setActionError(startError.message);
    }
    setBusyAction(null);
  }

  // StartGameForm's onStart. Errors go back to the form, which shows them.
  async function handleStartGame(servingTeam, serverId, receiverId) {
    setData(await startGame(eventId, matchId, servingTeam, serverId, receiverId));
  }

  if (loading) return <p className="page-status">Loading match…</p>;

  const backLink = (
    <Link to={`/events/${eventId}/courts`} className="back-link">
      ← Courts
    </Link>
  );

  if (error) {
    return (
      <section>
        {backLink}
        <p className="form-error" role="alert">
          {error.message}
        </p>
      </section>
    );
  }

  const busy = busyAction !== null;
  const game = board.currentGame; // null before game 1 starts
  const teams = { A: board.teamA, B: board.teamB };
  const step = board.nextStep;
  // Undo is possible while the match is being scored and something has happened.
  const canUndo = board.status === 'IN_PROGRESS' && board.games.length > 0;

  return (
    <section className="scoreboard">
      {backLink}
      <div className="page-header">
        <h1>Court {board.courtNumber}</h1>
        {/* Only while playing: between games the form already says "Game 2: who serves first?". */}
        {step === 'RECORD_POINT' && <span className="muted">Game {game.gameNumber}</span>}
      </div>

      {/* Finished games, e.g. "Game 1  21–18". Only useful once there's more than one. */}
      {board.games.length > 1 && (
        <ol className="game-chips" aria-label="Games">
          {board.games.map((g) => (
            <li key={g.gameNumber} className="game-chip">
              Game {g.gameNumber} <strong>{g.teamAScore}–{g.teamBScore}</strong>
            </li>
          ))}
        </ol>
      )}

      {/* The pop-up moment: the last rally means "change ends now". role="alert" makes
          screen readers announce it at once. */}
      {game?.changeEndsNow && step === 'RECORD_POINT' && (
        <p className="change-ends" role="alert">
          Change ends! Players switch sides of the court.
        </p>
      )}

      {actionError && (
        <p className="form-error" role="alert">
          {actionError}
        </p>
      )}

      {step === 'START_MATCH' && (
        <div className="card">
          <p>
            <strong>Team A</strong> {teamName(teams.A)} <span className="muted">vs</span>{' '}
            <strong>Team B</strong> {teamName(teams.B)}
          </p>
          <button type="button" className="button primary wide" disabled={busy} onClick={handleStartMatch}>
            {busyAction === 'start-match' ? 'Starting…' : 'Start match'}
          </button>
        </div>
      )}

      {step === 'START_GAME' && <StartGameForm scoreboard={board} onStart={handleStartGame} />}

      {step === 'RECORD_POINT' && (
        <>
          <p className="muted tap-hint">Tap the team that won the rally.</p>
          <div className="score-buttons">
            {['A', 'B'].map((team) => (
              <button
                key={team}
                type="button"
                className={game.servingTeam === team ? 'score-button serving' : 'score-button'}
                disabled={busy}
                onClick={() => run(`point-${team}`, () => recordPoint(eventId, matchId, team))}
                aria-label={`Point to Team ${team}, ${teamName(teams[team])}`}
              >
                <span className="score-team">Team {team}</span>
                <span className="score-value">{team === 'A' ? game.teamAScore : game.teamBScore}</span>
                <span className="score-names">{teamName(teams[team])}</span>
                {game.servingTeam === team && <span className="score-serving">Serving</span>}
              </button>
            ))}
          </div>
          <CourtDiagram game={game} />
        </>
      )}

      {step === 'COMPLETE_MATCH' && (
        <div className="card winner-card">
          <p className="winner-title">
            Team {board.matchWinner} wins! <span aria-hidden="true">🏆</span>
          </p>
          <p>{teamName(teams[board.matchWinner])}</p>
          <p className="muted">
            {board.games.map((g) => `${g.teamAScore}–${g.teamBScore}`).join(', ')}
          </p>
          <button
            type="button"
            className="button primary wide"
            disabled={busy}
            onClick={() => run('complete', () => completeMatch(eventId, matchId))}
          >
            {busyAction === 'complete' ? 'Saving…' : 'Confirm result'}
          </button>
        </div>
      )}

      {step === 'NONE' && board.status === 'COMPLETED' && (
        <div className="card winner-card">
          <p className="winner-title">
            Team {board.matchWinner} won <span aria-hidden="true">🏆</span>
          </p>
          <p>
            {teamName(teams[board.matchWinner])} beat {teamName(teams[board.matchWinner === 'A' ? 'B' : 'A'])}
          </p>
          <p className="muted">
            {board.games.map((g) => `${g.teamAScore}–${g.teamBScore}`).join(', ')}
            {board.durationSeconds != null && ` · ${formatDuration(board.durationSeconds)}`}
          </p>
          <Link to={`/events/${eventId}/courts`} className="button primary wide">
            Back to courts
          </Link>
        </div>
      )}

      {step === 'NONE' && board.status === 'CANCELLED' && (
        <p className="notice">This match was cancelled, so its score doesn&apos;t count.</p>
      )}

      {canUndo && (
        <button
          type="button"
          className="button wide undo-button"
          disabled={busy}
          onClick={() => run('undo', () => undoLast(eventId, matchId))}
        >
          {busyAction === 'undo' ? 'Undoing…' : '↶ Undo last'}
        </button>
      )}
    </section>
  );
}