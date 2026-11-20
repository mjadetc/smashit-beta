// A small picture of the court: who stands where, who serves and who receives.
// It's drawn as seen by the organizer standing at the bottom end of the court.
//
//   game = the backend's currentGame: server, receiver, servingTeam, endsChanged,
//          and the player in each service court (teamARightCourt, teamALeftCourt...)
//
// "Right" and "left" service courts are named from each PLAYER's view, facing the net.
// For the team at the bottom (facing up) that's the same as ours: left on the left.
// For the team at the top (facing us) it's mirrored: their right court is on OUR left.
export default function CourtDiagram({ game }) {
  // Teams start game 1 with A at the top. After a change of ends, B is at the top.
  const topTeam = game.endsChanged ? 'B' : 'A';
  const bottomTeam = topTeam === 'A' ? 'B' : 'A';

  // The two service courts of a team, as { side, player } objects.
  function courtsOf(team) {
    return {
      right: { side: 'Right', player: game[`team${team}RightCourt`] },
      left: { side: 'Left', player: game[`team${team}LeftCourt`] },
    };
  }

  const top = courtsOf(topTeam);
  const bottom = courtsOf(bottomTeam);

  return (
    // role="img" + aria-label: screen readers read one sentence instead of the boxes.
    <div
      className="court-diagram"
      role="img"
      aria-label={`${game.server.playerName} serves to ${game.receiver.playerName}`}
    >
      <CourtHalf team={topTeam} boxes={[top.right, top.left]} game={game} />
      <div className="net" aria-hidden="true">
        <span>net</span>
      </div>
      <CourtHalf team={bottomTeam} boxes={[bottom.left, bottom.right]} game={game} />
    </div>
  );
}

// One end of the court: the team's name and its two service courts.
function CourtHalf({ team, boxes, game }) {
  return (
    <div className="court-half">
      <span className="court-half-label">Team {team}</span>
      <div className="service-courts">
        {boxes.map(({ side, player }) => {
          const isServer = player.participantId === game.server.participantId;
          const isReceiver = player.participantId === game.receiver.participantId;
          let className = 'service-court';
          if (isServer) className += ' serving';
          if (isReceiver) className += ' receiving';
          return (
            <div key={side} className={className}>
              <span className="service-court-side">{side}</span>
              <span className="service-court-player">{player.playerName}</span>
              {isServer && <span className="service-court-role">Serving</span>}
              {isReceiver && <span className="service-court-role">Receiving</span>}
            </div>
          );
        })}
      </div>
    </div>
  );
}