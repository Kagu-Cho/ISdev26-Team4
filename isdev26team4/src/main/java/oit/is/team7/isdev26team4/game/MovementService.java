package oit.is.team7.isdev26team4.game;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;

/**
 * サイコロの出目生成と移動先・到着種別・次手番・月進行を判定する純粋計算サービス（Issue #7）。
 * 月次行動効果（#8）、過去問・科目合否（#9）、卒業・留年判定（#10）、DB保存（#11）は呼び出し側が扱う。
 */
@Service
public class MovementService {

  public static final int MIN_SQUARE_ID = 1;
  public static final int MAX_SQUARE_ID = 100;
  public static final int REMEDIAL_LOOP_START = 91;
  public static final int MIN_DICE = 1;
  public static final int MAX_DICE = 6;
  public static final int MIN_PLAYER_NUMBER = 1;
  public static final int MAX_PLAYER_NUMBER = 4;
  public static final int SEMESTER_INTERVAL_MONTHS = 6;

  public enum GameStatus {
    WAITING, PLAYING, FINISHED
  }

  /**
   * 移動後の到着イベント種別。
   * EVENT は docs/game-api.md の "交流" に対応するため、#12 のAPI応答変換時に "交流" へ変換する。
   * GOAL は新たにマス100へ到着したとき（movedFrom != 100 かつ movedTo == 100）のみ返す。
   */
  public enum LandingEventType {
    NONE, EVENT, GOAL
  }

  /**
   * 移動と手番判定に必要な参加者1人分の状態。
   * firstGoalMonth は未到達時は0、到達後は初回到達月（1以上）を保持する。
   */
  public record PlayerMoveState(
      int playerNumber,
      int squareId,
      EnrollmentStatus enrollmentStatus,
      boolean hasReachedGoal,
      int firstGoalMonth) {
    public PlayerMoveState {
      if (playerNumber < MIN_PLAYER_NUMBER || playerNumber > MAX_PLAYER_NUMBER) {
        throw new IllegalArgumentException("playerNumberは1〜4で指定してください");
      }
      if (squareId < MIN_SQUARE_ID || squareId > MAX_SQUARE_ID) {
        throw new IllegalArgumentException("squareIdは1〜100で指定してください");
      }
      if (enrollmentStatus == null) {
        throw new IllegalArgumentException("enrollmentStatusは必須です");
      }
      if (firstGoalMonth < 0 || (!hasReachedGoal && firstGoalMonth != 0)
          || (hasReachedGoal && firstGoalMonth == 0)) {
        throw new IllegalArgumentException("hasReachedGoalとfirstGoalMonthの整合が不正です");
      }
    }
  }

  /**
   * 移動・手番判定の対象となるゲーム状態。
   * players の並び順を手番順（時計回り固定）として扱う。
   */
  public record GameTurnState(
      GameStatus status,
      int month,
      int currentPlayerNumber,
      List<PlayerMoveState> players) {
    public GameTurnState {
      if (status == null) {
        throw new IllegalArgumentException("statusは必須です");
      }
      if (month < 1) {
        throw new IllegalArgumentException("monthは1以上で指定してください");
      }
      if (players == null || players.isEmpty() || players.size() > MAX_PLAYER_NUMBER) {
        throw new IllegalArgumentException("playersは1〜4人で指定してください");
      }
      players = List.copyOf(players);
      Set<Integer> seenNumbers = new HashSet<>();
      for (PlayerMoveState player : players) {
        if (player == null || !seenNumbers.add(player.playerNumber())) {
          throw new IllegalArgumentException("playersに重複またはnullがあります");
        }
      }
      // FINISHED 時のみ currentPlayerNumber=0 を許容し、PLAYING 時は 1〜4 に限定する
      int minCurrent = (status == GameStatus.PLAYING) ? MIN_PLAYER_NUMBER : 0;
      if (currentPlayerNumber < minCurrent || currentPlayerNumber > MAX_PLAYER_NUMBER) {
        throw new IllegalArgumentException("currentPlayerNumberが不正です");
      }
    }
  }

  /**
   * 移動および手番更新の計算結果。
   * roundCompleted=true のとき、当月の全未卒業者が手番を終えたことを表す。
   */
  public record MoveOutcome(
      int actorPlayerNumber,
      int rolledNumber,
      int movedFrom,
      int movedTo,
      LandingEventType landingEventType,
      boolean hasReachedGoal,
      int firstGoalMonth,
      int nextPlayerNumber,
      int nextMonth,
      boolean roundCompleted,
      boolean semesterAssessmentDue) {
  }

  private final RandomGenerator random;

  public MovementService() {
    this(new Random());
  }

  /** テストでサイコロ出目乱数を固定するためのコンストラクタ。 */
  public MovementService(RandomGenerator random) {
    if (random == null) {
      throw new IllegalArgumentException("乱数生成器は必須です");
    }
    this.random = random;
  }

  /** 6面サイコロの出目（1〜6）を生成する。 */
  public int rollDice() {
    return random.nextInt(MIN_DICE, MAX_DICE + 1);
  }

  /** サーバー側で生成した6面サイコロを用いて移動と次手番を計算する。 */
  public MoveOutcome moveWithRandomDice(GameTurnState state, int actorPlayerNumber) {
    return moveAndAdvanceTurn(state, actorPlayerNumber, rollDice());
  }

  /**
   * 固定の出目を指定して移動先・到着種別・次手番・月進行を計算する（入力状態は変更しない）。
   */
  public MoveOutcome moveAndAdvanceTurn(GameTurnState state, int actorPlayerNumber, int rolledNumber) {
    if (state == null) {
      throw new IllegalArgumentException("ゲーム状態は必須です");
    }
    if (rolledNumber < MIN_DICE || rolledNumber > MAX_DICE) {
      throw new IllegalArgumentException("サイコロの出目は1〜6で指定してください");
    }
    if (state.status() != GameStatus.PLAYING) {
      throw new IllegalArgumentException("進行中(PLAYING)のゲームのみ移動できます");
    }
    if (actorPlayerNumber != state.currentPlayerNumber()) {
      throw new IllegalArgumentException("現在の手番参加者ではありません");
    }

    int actorIndex = findPlayerIndex(state.players(), actorPlayerNumber);
    PlayerMoveState actor = state.players().get(actorIndex);
    if (actor.enrollmentStatus() == EnrollmentStatus.GRADUATED) {
      throw new IllegalArgumentException("卒業済み参加者は移動できません");
    }

    int movedFrom = actor.squareId();
    int movedTo = computeNextSquare(movedFrom, rolledNumber, actor.enrollmentStatus());
    boolean reachedGoal = actor.hasReachedGoal() || movedTo == MAX_SQUARE_ID;
    int firstGoalMonth = actor.hasReachedGoal()
        ? actor.firstGoalMonth()
        : (movedTo == MAX_SQUARE_ID ? state.month() : 0);
    LandingEventType landingEventType = resolveLandingEvent(movedFrom, movedTo, actor.enrollmentStatus());

    int nextInRoundIndex = findNextActiveIndex(state.players(), actorIndex + 1, state.players().size());
    boolean roundCompleted = nextInRoundIndex < 0;
    int nextIndex = roundCompleted
        ? findNextActiveIndex(state.players(), 0, actorIndex + 1)
        : nextInRoundIndex;

    int nextPlayerNumber = state.players().get(nextIndex).playerNumber();
    int nextMonth = roundCompleted ? state.month() + 1 : state.month();
    boolean semesterAssessmentDue = roundCompleted && (state.month() % SEMESTER_INTERVAL_MONTHS == 0);

    return new MoveOutcome(
        actorPlayerNumber,
        rolledNumber,
        movedFrom,
        movedTo,
        landingEventType,
        reachedGoal,
        firstGoalMonth,
        nextPlayerNumber,
        nextMonth,
        roundCompleted,
        semesterAssessmentDue);
  }

  private static int computeNextSquare(int currentSquare, int rolledNumber, EnrollmentStatus enrollment) {
    if (enrollment != EnrollmentStatus.REMEDIAL) {
      return Math.min(MAX_SQUARE_ID, currentSquare + rolledNumber);
    }
    int pos = currentSquare;
    for (int step = 0; step < rolledNumber; step++) {
      pos = (pos >= MAX_SQUARE_ID) ? REMEDIAL_LOOP_START : pos + 1;
    }
    return pos;
  }

  private static LandingEventType resolveLandingEvent(int movedFrom, int movedTo, EnrollmentStatus enrollment) {
    if (movedTo == MAX_SQUARE_ID) {
      return (movedFrom != MAX_SQUARE_ID) ? LandingEventType.GOAL : LandingEventType.NONE;
    }
    if (enrollment != EnrollmentStatus.REMEDIAL && isExchangeSquare(movedTo)) {
      return LandingEventType.EVENT;
    }
    return LandingEventType.NONE;
  }

  // 通常盤面の交流マスは 10, 20, 30, 40, 60, 70, 80, 90（50 は試験マス、100 は GOAL）
  private static boolean isExchangeSquare(int squareId) {
    return squareId % 10 == 0 && squareId != 50 && squareId < MAX_SQUARE_ID;
  }

  private static int findPlayerIndex(List<PlayerMoveState> players, int playerNumber) {
    for (int i = 0; i < players.size(); i++) {
      if (players.get(i).playerNumber() == playerNumber) {
        return i;
      }
    }
    throw new IllegalArgumentException("指定された参加者が見つかりません");
  }

  private static int findNextActiveIndex(List<PlayerMoveState> players, int fromInclusive, int toExclusive) {
    for (int i = fromInclusive; i < toExclusive; i++) {
      if (players.get(i).enrollmentStatus() != EnrollmentStatus.GRADUATED) {
        return i;
      }
    }
    return -1;
  }
}
