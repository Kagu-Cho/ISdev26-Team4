package oit.is.team7.isdev26team4.event;

import org.springframework.stereotype.Service;

/**
 * 月次行動（勉強・サークル・アルバイト）による学力・人脈・所持金の変化と、年度替わりの年部費を計算する。
 * 過去問抽選と科目合否（#9）、状態の保存（#11）、手番進行（#12）は扱わない。
 * 入力の状態は変更せず、計算後の新しい状態を返す。
 */
@Service
public class MonthlyActionService {

  // 共有仕様（#2）で確定した値
  public static final int ANNUAL_CIRCLE_FEE = 3000;
  public static final int PART_TIME_INCOME = 20000;

  // TODO #2 docs/game-rules.md の確定後に値を合わせる（暫定値）
  public static final int ACADEMIC_MIN = 0;
  public static final int ACADEMIC_MAX = 100;
  public static final int STUDY_GAIN = 10;
  public static final int FORGET_LOSS = 2;
  public static final int CIRCLE_CONNECTION_GAIN = 1;

  public enum MonthlyAction {
    STUDY, CIRCLE, PART_TIME
  }

  /** 月次行動の計算に必要な学生1人分の状態。 */
  public record StudentStatus(int academic, int connections, int money, boolean circleMember) {
  }

  /** 月次行動の結果。circleRejected はサークルを選んだが部費不足で参加できなかったことを表す。 */
  public record MonthlyResult(StudentStatus status, boolean circleRejected) {
  }

  /** 年度替わりの部費処理の結果。withdrawn は部費不足で退部になったことを表す。 */
  public record FeeResult(StudentStatus status, boolean feePaid, boolean withdrawn) {
  }

  public MonthlyResult applyMonthlyAction(StudentStatus current, MonthlyAction action) {
    if (current == null || action == null) {
      throw new IllegalArgumentException("状態と月次行動は必須です");
    }
    return switch (action) {
      case STUDY -> new MonthlyResult(withAcademic(current, current.academic() + STUDY_GAIN), false);
      case PART_TIME -> new MonthlyResult(
          forget(new StudentStatus(current.academic(), current.connections(),
              current.money() + PART_TIME_INCOME, current.circleMember())),
          false);
      case CIRCLE -> applyCircle(current);
    };
  }

  /**
   * 年度が変わるときに呼ぶ。サークル参加者から年部費を徴収し、払えない場合は退部させる。
   * 所持金がマイナスにならないよう、不足時は徴収せず退部扱いとする（暫定規則）。
   */
  public FeeResult collectAnnualFee(StudentStatus current) {
    if (current == null) {
      throw new IllegalArgumentException("状態は必須です");
    }
    if (!current.circleMember()) {
      return new FeeResult(current, false, false);
    }
    if (current.money() < ANNUAL_CIRCLE_FEE) {
      StudentStatus withdrawn = new StudentStatus(current.academic(), current.connections(),
          current.money(), false);
      return new FeeResult(withdrawn, false, true);
    }
    StudentStatus paid = new StudentStatus(current.academic(), current.connections(),
        current.money() - ANNUAL_CIRCLE_FEE, true);
    return new FeeResult(paid, true, false);
  }

  // 未参加者は入部時にその年度分の部費を払う。払えなければ人脈は増えない（月の行動は消費される）。
  private MonthlyResult applyCircle(StudentStatus current) {
    int money = current.money();
    if (!current.circleMember()) {
      if (money < ANNUAL_CIRCLE_FEE) {
        return new MonthlyResult(forget(current), true);
      }
      money -= ANNUAL_CIRCLE_FEE;
    }
    StudentStatus joined = new StudentStatus(current.academic(),
        current.connections() + CIRCLE_CONNECTION_GAIN, money, true);
    return new MonthlyResult(forget(joined), false);
  }

  // 勉強しなかった月の忘却
  private StudentStatus forget(StudentStatus s) {
    return withAcademic(s, s.academic() - FORGET_LOSS);
  }

  private StudentStatus withAcademic(StudentStatus s, int academic) {
    int clamped = Math.max(ACADEMIC_MIN, Math.min(ACADEMIC_MAX, academic));
    return new StudentStatus(clamped, s.connections(), s.money(), s.circleMember());
  }
}
