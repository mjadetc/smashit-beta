package com.smashit_beta.backend.service;

import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.Team;

import java.util.List;

// The badminton doubles rules (FR-024 to FR-032), with no database and no Spring.
// Like LineupFiller, it takes plain values and returns plain values, so it's easy to test.
//
// The key idea is REPLAY: we never store "who serves now". We store how the game
// started plus the winner of every rally, and replay them from 0-0. Undo is then
// trivial: remove the last rally and replay again.
//
// The rules (rally scoring, BWF Laws of Badminton):
//  - Every rally wins a point. A game is won at 21 with a 2-point lead;
//    at 29-29 the next point wins (30 is the maximum).
//  - The serving team serves from its RIGHT service court when its own score is
//    even, and from the LEFT when it's odd. The receiver is the opponent standing
//    diagonally opposite, i.e. in the same-named (right/left) service court.
//  - Serving team wins the rally: same server, who switches service courts with
//    their partner. The receiving team doesn't move.
//  - Receiving team wins the rally: they become the serving team. NOBODY moves;
//    the new server is whoever is in the court that matches their new score.
//  - Ends: teams change ends after game 1 and after game 2, and in game 3 when the
//    leading team reaches 11. A single-game match changes ends at the organizer's
//    chosen score (FR-025), or never.
public final class BadmintonRules {

    public static final int POINTS_TO_WIN = 21;
    public static final int MAX_POINTS = 30;
    public static final int DECIDING_GAME_CHANGE_ENDS_AT = 11;

    // "final" class + private constructor = a toolbox of static methods,
    // nobody can create a BadmintonRules object (like java.lang.Math).
    private BadmintonRules() {
    }

    // How a game starts. Each player is identified by their participant id.
    //   server   / serverPartner   = the serving team (server stands RIGHT at 0-0)
    //   receiver / receiverPartner = the other team   (receiver stands RIGHT at 0-0)
    public record GameSetup(
            MatchFormat format,
            Integer changeEndsAt,
            int gameNumber,
            Team servingTeam,
            Long serverId,
            Long serverPartnerId,
            Long receiverId,
            Long receiverPartnerId
    ) {
    }

    // Everything the court screen needs after the rallies so far (FR-027, FR-028, FR-030).
    //   endsChanged    = the teams are on the opposite ends to where they began game 1
    //   changeEndsNow  = the LAST rally means "change ends now" (for a pop-up in the UI)
    //   winner         = null while the game is still being played (FR-031)
    public record GameState(
            int teamAScore,
            int teamBScore,
            Team servingTeam,
            Long serverId,
            Long receiverId,
            Long teamARightCourtId,
            Long teamALeftCourtId,
            Long teamBRightCourtId,
            Long teamBLeftCourtId,
            boolean endsChanged,
            boolean changeEndsNow,
            Team winner
    ) {
        public boolean isOver() {
            return winner != null;
        }
    }

    // Replays the rallies from 0-0 and returns the state after the last one.
    public static GameState replay(GameSetup setup, List<Team> rallyWinners) {
        // Mutable copies of the state, updated rally by rally.
        int scoreA = 0;
        int scoreB = 0;
        Team serving = setup.servingTeam();
        Long server = setup.serverId();

        // Who stands in each team's right/left service court. At 0-0 the server
        // and the receiver are both on the right.
        Long aRight;
        Long aLeft;
        Long bRight;
        Long bLeft;
        if (serving == Team.A) {
            aRight = setup.serverId();
            aLeft = setup.serverPartnerId();
            bRight = setup.receiverId();
            bLeft = setup.receiverPartnerId();
        } else {
            bRight = setup.serverId();
            bLeft = setup.serverPartnerId();
            aRight = setup.receiverId();
            aLeft = setup.receiverPartnerId();
        }

        Integer midGameChange = midGameChangeEndsAt(setup);
        boolean changeEndsNow = false;

        for (Team rallyWinner : rallyWinners) {
            if (gameWinner(scoreA, scoreB) != null) {
                // The service never lets this happen; it would mean corrupt data.
                throw new IllegalStateException("A rally was recorded after the game ended.");
            }
            int leaderBefore = Math.max(scoreA, scoreB);

            if (rallyWinner == Team.A) {
                scoreA++;
            } else {
                scoreB++;
            }

            if (rallyWinner == serving) {
                // Same server, who switches service courts with their partner.
                if (serving == Team.A) {
                    Long swap = aRight;
                    aRight = aLeft;
                    aLeft = swap;
                } else {
                    Long swap = bRight;
                    bRight = bLeft;
                    bLeft = swap;
                }
            } else {
                // Service passes to the other team. Nobody moves; the new server is
                // whoever stands in the court that matches their (new) score.
                serving = rallyWinner;
                int newScore = serving == Team.A ? scoreA : scoreB;
                if (serving == Team.A) {
                    server = isEven(newScore) ? aRight : aLeft;
                } else {
                    server = isEven(newScore) ? bRight : bLeft;
                }
            }

            // "Change ends now" = this rally took the leading score up to the threshold.
            changeEndsNow = midGameChange != null
                    && leaderBefore < midGameChange
                    && Math.max(scoreA, scoreB) >= midGameChange
                    && gameWinner(scoreA, scoreB) == null;
        }

        // The receiver stands diagonally opposite the server: right if the
        // serving team's score is even, left if it's odd.
        boolean fromRight = isEven(serving == Team.A ? scoreA : scoreB);
        Long receiver = serving == Team.A
                ? (fromRight ? bRight : bLeft)
                : (fromRight ? aRight : aLeft);

        // Game 2 is played on swapped ends; games 1 and 3 start on the original ends.
        // The mid-game change (game 3 at 11, or the organizer's score) flips it once more.
        boolean endsChanged = setup.gameNumber() == 2;
        if (midGameChange != null && Math.max(scoreA, scoreB) >= midGameChange) {
            endsChanged = !endsChanged;
        }

        return new GameState(scoreA, scoreB, serving, server, receiver,
                aRight, aLeft, bRight, bLeft, endsChanged, changeEndsNow, gameWinner(scoreA, scoreB));
    }

    // FR-031: who has won the game at this score, or null if it's not over.
    public static Team gameWinner(int scoreA, int scoreB) {
        if (isGameWon(scoreA, scoreB)) {
            return Team.A;
        }
        if (isGameWon(scoreB, scoreA)) {
            return Team.B;
        }
        return null;
    }

    // How many games a team must win to win the match.
    public static int gamesNeededToWin(MatchFormat format) {
        return format == MatchFormat.BEST_OF_THREE ? 2 : 1;
    }

    // FR-032: the match winner from the winners of the finished games, or null if undecided.
    // (Pass null for a game that's still in progress; it doesn't count for anyone.)
    public static Team matchWinner(MatchFormat format, List<Team> gameWinners) {
        int needed = gamesNeededToWin(format);
        long winsA = gameWinners.stream().filter(team -> team == Team.A).count();
        long winsB = gameWinners.stream().filter(team -> team == Team.B).count();
        if (winsA >= needed) {
            return Team.A;
        }
        if (winsB >= needed) {
            return Team.B;
        }
        return null;
    }

    // 21 with a 2-point lead, or 30 (the cap after 29-29).
    private static boolean isGameWon(int score, int opponentScore) {
        return score == MAX_POINTS || (score >= POINTS_TO_WIN && score - opponentScore >= 2);
    }

    // At which score the ends change DURING this game, or null for no change.
    private static Integer midGameChangeEndsAt(GameSetup setup) {
        if (setup.format() == MatchFormat.BEST_OF_THREE) {
            return setup.gameNumber() == 3 ? DECIDING_GAME_CHANGE_ENDS_AT : null;
        }
        // SINGLE_GAME: the organizer's choice (null = never).
        return setup.changeEndsAt();
    }

    private static boolean isEven(int number) {
        return number % 2 == 0;
    }
}