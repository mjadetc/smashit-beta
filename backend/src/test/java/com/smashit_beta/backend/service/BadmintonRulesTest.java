package com.smashit_beta.backend.service;

import com.smashit_beta.backend.entity.MatchFormat;
import com.smashit_beta.backend.entity.Team;
import com.smashit_beta.backend.service.BadmintonRules.GameSetup;
import com.smashit_beta.backend.service.BadmintonRules.GameState;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Plain unit tests for the rules: no Spring, no Mockito, no database.
// The players (participant ids):
//   team A: LEO = 1, ANA = 2      team B: FRANCE = 3, BEA = 4
// Every game below starts with Leo serving to France (both in the right service court),
// unless the test says otherwise.
class BadmintonRulesTest {

    private static final Long LEO = 1L;
    private static final Long ANA = 2L;
    private static final Long FRANCE = 3L;
    private static final Long BEA = 4L;

    private static final Team A = Team.A;
    private static final Team B = Team.B;

    // Game `gameNumber` of a best-of-three match, Leo serving to France.
    private GameSetup bestOfThree(int gameNumber) {
        return new GameSetup(MatchFormat.BEST_OF_THREE, null, gameNumber, A, LEO, ANA, FRANCE, BEA);
    }

    // A single-game match with this change-of-ends score (null = no change).
    private GameSetup singleGame(Integer changeEndsAt) {
        return new GameSetup(MatchFormat.SINGLE_GAME, changeEndsAt, 1, A, LEO, ANA, FRANCE, BEA);
    }

    // `count` rallies all won by `team`.
    private List<Team> times(int count, Team team) {
        return new ArrayList<>(Collections.nCopies(count, team));
    }

    // Joins lists of rallies into one list, in order.
    @SafeVarargs
    private List<Team> rallies(List<Team>... parts) {
        List<Team> all = new ArrayList<>();
        Arrays.stream(parts).forEach(all::addAll);
        return all;
    }

    @Test
    void shouldStartWithServerAndReceiverInTheRightCourts() {
        GameState state = BadmintonRules.replay(bestOfThree(1), List.of());

        assertEquals(0, state.teamAScore());
        assertEquals(A, state.servingTeam());
        assertEquals(LEO, state.serverId());
        assertEquals(FRANCE, state.receiverId());
        assertEquals(LEO, state.teamARightCourtId());
        assertEquals(ANA, state.teamALeftCourtId());
        assertEquals(FRANCE, state.teamBRightCourtId());
        assertEquals(BEA, state.teamBLeftCourtId());
        assertNull(state.winner());
    }

    @Test
    void shouldKeepServerAndSwitchTheirCourtWhenServingTeamWinsRally() {
        // 1-0 to A: Leo serves again, now from the left (odd score), to Bea.
        GameState state = BadmintonRules.replay(bestOfThree(1), List.of(A));

        assertEquals(1, state.teamAScore());
        assertEquals(LEO, state.serverId());
        assertEquals(LEO, state.teamALeftCourtId());
        assertEquals(ANA, state.teamARightCourtId());
        // The receiving team didn't move.
        assertEquals(FRANCE, state.teamBRightCourtId());
        assertEquals(BEA, state.receiverId());
    }

    @Test
    void shouldPassServiceWithoutMovingAnyoneWhenReceivingTeamWinsRally() {
        // 0-1: B serves. B's score is odd, so the player in B's LEFT court (Bea) serves,
        // to the player in A's left court (Ana).
        GameState state = BadmintonRules.replay(bestOfThree(1), List.of(B));

        assertEquals(B, state.servingTeam());
        assertEquals(BEA, state.serverId());
        assertEquals(ANA, state.receiverId());
        // Nobody moved.
        assertEquals(LEO, state.teamARightCourtId());
        assertEquals(FRANCE, state.teamBRightCourtId());
    }

    @Test
    void shouldFollowServeThroughSeveralRallies() {
        // A, A -> 2-0: Leo serves from the right again (he switched twice), to France.
        // then B -> 2-1: B serves, odd score: Bea (left) serves to the A player on the left.
        //   After two switches A is back to Leo right, Ana left, so the receiver is Ana.
        // then B -> 2-2: Bea serves again, now from the right, to Leo.
        GameState state = BadmintonRules.replay(bestOfThree(1), List.of(A, A, B, B));

        assertEquals(2, state.teamAScore());
        assertEquals(2, state.teamBScore());
        assertEquals(B, state.servingTeam());
        assertEquals(BEA, state.serverId());
        assertEquals(BEA, state.teamBRightCourtId());
        assertEquals(FRANCE, state.teamBLeftCourtId());
        assertEquals(LEO, state.receiverId());
    }

    @Test
    void shouldWinGameAt21WithTwoPointLead() {
        assertEquals(A, BadmintonRules.replay(bestOfThree(1), times(21, A)).winner());
        assertEquals(A, BadmintonRules.gameWinner(21, 19));
        assertEquals(B, BadmintonRules.gameWinner(17, 21));
        assertNull(BadmintonRules.gameWinner(20, 19));
    }

    @Test
    void shouldNeedTwoPointLeadAfter20All() {
        assertNull(BadmintonRules.gameWinner(21, 20));
        assertNull(BadmintonRules.gameWinner(28, 27));
        assertEquals(A, BadmintonRules.gameWinner(22, 20));
    }

    @Test
    void shouldEndGameAt30After29All() {
        // At 29-29 the next point wins, even with a 1-point lead.
        assertEquals(B, BadmintonRules.gameWinner(29, 30));
        assertNull(BadmintonRules.gameWinner(29, 29));
    }

    @Test
    void shouldNotAllowRalliesAfterTheGameIsOver() {
        assertThrows(IllegalStateException.class,
                () -> BadmintonRules.replay(bestOfThree(1), times(22, A)));
    }

    @Test
    void shouldChangeEndsBetweenGamesOfBestOfThree() {
        assertFalse(BadmintonRules.replay(bestOfThree(1), times(15, A)).endsChanged());
        assertTrue(BadmintonRules.replay(bestOfThree(2), List.of()).endsChanged());
        assertFalse(BadmintonRules.replay(bestOfThree(3), List.of()).endsChanged());
    }

    @Test
    void shouldChangeEndsAt11InTheDecidingGame() {
        // 10-5: not yet.
        GameState at10 = BadmintonRules.replay(bestOfThree(3), rallies(times(5, B), times(10, A)));
        assertFalse(at10.endsChanged());
        assertFalse(at10.changeEndsNow());

        // 11-5: change ends now.
        GameState at11 = BadmintonRules.replay(bestOfThree(3), rallies(times(5, B), times(11, A)));
        assertTrue(at11.endsChanged());
        assertTrue(at11.changeEndsNow());

        // 11-6: still on the changed ends, but no new pop-up.
        GameState after = BadmintonRules.replay(bestOfThree(3), rallies(times(5, B), times(11, A), List.of(B)));
        assertTrue(after.endsChanged());
        assertFalse(after.changeEndsNow());
    }

    @Test
    void shouldUseOrganizersChangeOfEndsInSingleGame() {
        // FR-025: the organizer chose 15.
        GameState at14 = BadmintonRules.replay(singleGame(15), times(14, B));
        assertFalse(at14.endsChanged());

        GameState at15 = BadmintonRules.replay(singleGame(15), times(15, B));
        assertTrue(at15.endsChanged());
        assertTrue(at15.changeEndsNow());

        // No change of ends chosen: the teams never switch.
        assertFalse(BadmintonRules.replay(singleGame(null), times(20, B)).endsChanged());
    }

    @Test
    void shouldDecideMatchWinnerByFormat() {
        // Single game: one game decides it.
        assertEquals(B, BadmintonRules.matchWinner(MatchFormat.SINGLE_GAME, List.of(B)));

        // Best of three: 1-1 is undecided, 2-1 wins. A null (in-progress) game counts for nobody.
        assertNull(BadmintonRules.matchWinner(MatchFormat.BEST_OF_THREE, List.of(A, B)));
        assertNull(BadmintonRules.matchWinner(MatchFormat.BEST_OF_THREE, Arrays.asList(A, B, null)));
        assertEquals(A, BadmintonRules.matchWinner(MatchFormat.BEST_OF_THREE, List.of(A, B, A)));
        assertEquals(B, BadmintonRules.matchWinner(MatchFormat.BEST_OF_THREE, List.of(B, B)));
    }
}