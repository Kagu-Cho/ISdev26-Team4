package oit.is.team7.isdev26team4.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import oit.is.team7.isdev26team4.game.MovementService.GameStatus;
import oit.is.team7.isdev26team4.game.MovementService.GameTurnState;
import oit.is.team7.isdev26team4.game.MovementService.LandingEventType;
import oit.is.team7.isdev26team4.game.MovementService.MoveOutcome;
import oit.is.team7.isdev26team4.game.MovementService.PlayerMoveState;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;

class MovementServiceTest {

  private final MovementService service = new MovementService();

  private static GameTurnState standardFourPlayers(int month, int currentPlayerNumber) {
    return new GameTurnState(
        GameStatus.PLAYING,
        month,
        currentPlayerNumber,
        List.of(
            new PlayerMoveState(1, 1, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(2, 15, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(3, 44, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(4, 50, EnrollmentStatus.ENROLLED, false, 0)));
  }

  @Test
  void moveNormalAndAdvanceToNextPlayerWithoutAdvancingMonth() {
    GameTurnState state = standardFourPlayers(1, 1);

    MoveOutcome outcome = service.moveAndAdvanceTurn(state, 1, 4);

    assertEquals(1, outcome.movedFrom());
    assertEquals(5, outcome.movedTo());
    assertEquals(LandingEventType.NONE, outcome.landingEventType());
    assertFalse(outcome.hasReachedGoal());
    assertEquals(0, outcome.firstGoalMonth());
    assertEquals(2, outcome.nextPlayerNumber());
    assertEquals(1, outcome.nextMonth());
    assertFalse(outcome.roundCompleted());
    assertFalse(outcome.semesterAssessmentDue());
  }

  @Test
  void landingOnExchangeSquareSetsEventExceptSquare50() {
    GameTurnState exchangeState = new GameTurnState(
        GameStatus.PLAYING,
        2,
        1,
        List.of(
            new PlayerMoveState(1, 7, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(2, 46, EnrollmentStatus.ENROLLED, false, 0)));

    MoveOutcome atTen = service.moveAndAdvanceTurn(exchangeState, 1, 3);
    assertEquals(10, atTen.movedTo());
    assertEquals(LandingEventType.EVENT, atTen.landingEventType());

    GameTurnState examState = new GameTurnState(
        GameStatus.PLAYING,
        2,
        2,
        exchangeState.players());
    MoveOutcome atFifty = service.moveAndAdvanceTurn(examState, 2, 4);
    assertEquals(50, atFifty.movedTo());
    assertEquals(LandingEventType.NONE, atFifty.landingEventType());
  }

  @Test
  void enrolledPlayerClampsAt100AndRecordsFirstGoalMonthOnce() {
    GameTurnState firstReachState = new GameTurnState(
        GameStatus.PLAYING,
        21,
        1,
        List.of(
            new PlayerMoveState(1, 97, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(2, 80, EnrollmentStatus.ENROLLED, false, 0)));

    MoveOutcome firstOutcome = service.moveAndAdvanceTurn(firstReachState, 1, 6);

    assertEquals(100, firstOutcome.movedTo());
    assertEquals(LandingEventType.GOAL, firstOutcome.landingEventType());
    assertTrue(firstOutcome.hasReachedGoal());
    assertEquals(21, firstOutcome.firstGoalMonth());

    GameTurnState alreadyReachedState = new GameTurnState(
        GameStatus.PLAYING,
        22,
        1,
        List.of(
            new PlayerMoveState(1, 100, EnrollmentStatus.ENROLLED, true, 21),
            new PlayerMoveState(2, 82, EnrollmentStatus.ENROLLED, false, 0)));

    MoveOutcome stayOutcome = service.moveAndAdvanceTurn(alreadyReachedState, 1, 5);

    assertEquals(100, stayOutcome.movedTo());
    assertEquals(LandingEventType.NONE, stayOutcome.landingEventType());
    assertTrue(stayOutcome.hasReachedGoal());
    assertEquals(21, stayOutcome.firstGoalMonth());
  }

  @Test
  void remedialPlayerLoopsBetween91And100AndTriggersGoalOnlyWhenLandingOn100() {
    GameTurnState loopPast100 = new GameTurnState(
        GameStatus.PLAYING,
        25,
        1,
        List.of(
            new PlayerMoveState(1, 98, EnrollmentStatus.REMEDIAL, true, 21),
            new PlayerMoveState(2, 95, EnrollmentStatus.REMEDIAL, true, 22)));

    MoveOutcome outcome93 = service.moveAndAdvanceTurn(loopPast100, 1, 5);
    assertEquals(93, outcome93.movedTo());
    assertEquals(LandingEventType.NONE, outcome93.landingEventType());
    assertTrue(outcome93.hasReachedGoal());
    assertEquals(21, outcome93.firstGoalMonth());

    GameTurnState landExact100 = new GameTurnState(
        GameStatus.PLAYING,
        25,
        2,
        loopPast100.players());
    MoveOutcome outcome100 = service.moveAndAdvanceTurn(landExact100, 2, 5);
    assertEquals(100, outcome100.movedTo());
    assertEquals(LandingEventType.GOAL, outcome100.landingEventType());

    GameTurnState from100 = new GameTurnState(
        GameStatus.PLAYING,
        26,
        1,
        List.of(new PlayerMoveState(1, 100, EnrollmentStatus.REMEDIAL, true, 21)));
    MoveOutcome outcome96 = service.moveAndAdvanceTurn(from100, 1, 6);
    assertEquals(96, outcome96.movedTo());
    assertEquals(LandingEventType.NONE, outcome96.landingEventType());
  }

  @Test
  void skipsGraduatedPlayersAndTriggersSemesterAssessmentAtEverySixthMonth() {
    GameTurnState midRoundSkip = new GameTurnState(
        GameStatus.PLAYING,
        6,
        1,
        List.of(
            new PlayerMoveState(1, 40, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(2, 100, EnrollmentStatus.GRADUATED, true, 12),
            new PlayerMoveState(3, 60, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(4, 100, EnrollmentStatus.GRADUATED, true, 12)));

    MoveOutcome toPlayer3 = service.moveAndAdvanceTurn(midRoundSkip, 1, 1);
    assertEquals(3, toPlayer3.nextPlayerNumber());
    assertEquals(6, toPlayer3.nextMonth());
    assertFalse(toPlayer3.roundCompleted());
    assertFalse(toPlayer3.semesterAssessmentDue());

    GameTurnState endRoundWithGraduatedHead = new GameTurnState(
        GameStatus.PLAYING,
        6,
        3,
        List.of(
            new PlayerMoveState(1, 100, EnrollmentStatus.GRADUATED, true, 12),
            new PlayerMoveState(2, 70, EnrollmentStatus.ENROLLED, false, 0),
            new PlayerMoveState(3, 91, EnrollmentStatus.REMEDIAL, true, 6),
            new PlayerMoveState(4, 100, EnrollmentStatus.GRADUATED, true, 12)));

    MoveOutcome roundEnd = service.moveAndAdvanceTurn(endRoundWithGraduatedHead, 3, 2);
    assertEquals(93, roundEnd.movedTo());
    assertEquals(2, roundEnd.nextPlayerNumber());
    assertEquals(7, roundEnd.nextMonth());
    assertTrue(roundEnd.roundCompleted());
    assertTrue(roundEnd.semesterAssessmentDue());
  }

  @Test
  void moveWithRandomDiceUsesInjectedGeneratorRange1To6() {
    MovementService fixedDiceService = new MovementService(new RandomGenerator() {
      @Override
      public long nextLong() {
        return 0L;
      }

      @Override
      public int nextInt(int origin, int bound) {
        assertEquals(1, origin);
        assertEquals(7, bound);
        return 6;
      }
    });

    MoveOutcome outcome = fixedDiceService.moveWithRandomDice(standardFourPlayers(1, 1), 1);
    assertEquals(6, outcome.rolledNumber());
    assertEquals(7, outcome.movedTo());
  }

  @Test
  void rejectsInvalidDiceTurnStatusOrGraduatedActor() {
    GameTurnState playing = standardFourPlayers(1, 1);

    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(playing, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(playing, 1, 7));
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(playing, 2, 3));
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(null, 1, 3));
    assertThrows(IllegalArgumentException.class,
        () -> new GameTurnState(GameStatus.PLAYING, 1, 0, playing.players()));

    GameTurnState waiting = new GameTurnState(GameStatus.WAITING, 1, 1, playing.players());
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(waiting, 1, 3));

    GameTurnState finished = new GameTurnState(GameStatus.FINISHED, 12, 0, playing.players());
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(finished, 0, 3));

    GameTurnState graduatedActor = new GameTurnState(
        GameStatus.PLAYING,
        12,
        1,
        List.of(new PlayerMoveState(1, 100, EnrollmentStatus.GRADUATED, true, 12)));
    assertThrows(IllegalArgumentException.class, () -> service.moveAndAdvanceTurn(graduatedActor, 1, 3));
  }
}
