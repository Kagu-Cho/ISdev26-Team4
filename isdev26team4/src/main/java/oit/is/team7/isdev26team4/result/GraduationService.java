package oit.is.team7.isdev26team4.result;

import org.springframework.stereotype.Service;

/**
 * 卒業・留年の判定のみを行う。入力を変更せず、新しい状態を返す。
 * 盤面、半期決算、DB保存、順位の採番は呼び出し側の責任とする。
 */
@Service
public class GraduationService {

  public static final int REQUIRED_CREDITS = 124;

  public enum EnrollmentStatus {
    ENROLLED, REMEDIAL, GRADUATED
  }

  /**
   * #2 の共有型が未確定のため、本サービス用の受け渡し型とする。
   * lastAssessmentNumber は未判定なら0、graduationRank は未卒業なら0。
   * REMEDIAL は補講への移行要求を表し、位置や単位はここでは変えない。
   */
  public record StudentStatus(int credits, int repeatYears, EnrollmentStatus enrollment,
      int graduationRank, long lastAssessmentNumber) {
    public StudentStatus {
      if (credits < 0 || repeatYears < 0 || enrollment == null || lastAssessmentNumber < 0
          || (enrollment == EnrollmentStatus.GRADUATED ? graduationRank <= 0 : graduationRank != 0)) {
        throw new IllegalArgumentException("学生状態が不正です");
      }
    }
  }

  /**
   * assessmentNumber は学生ごとに増加する判定機会の番号（1以上）。再送は同じ番号を使う。
   * assessmentDue は卒業判定時点に達したかどうか。具体的な地点・月は #2 確定後に接続する。
   * graduationRank は呼び出し側が採番した順位（卒業成立時にのみ1以上が必要）。
   */
  public record AssessmentContext(long assessmentNumber, boolean assessmentDue, int graduationRank) {
    public AssessmentContext {
      if (assessmentNumber <= 0 || graduationRank < 0) {
        throw new IllegalArgumentException("判定情報が不正です");
      }
    }
  }

  /** 未確定の判定時点を差し替えるための境界。副作用のない関数として実装する。 */
  @FunctionalInterface
  public interface AssessmentRule {
    boolean isDue(StudentStatus student, AssessmentContext context);
  }

  private final AssessmentRule assessmentRule;

  /** 暫定ルール: 呼び出し側の判定時点フラグをそのまま使う。 */
  public GraduationService() {
    this((student, context) -> context.assessmentDue());
  }

  public GraduationService(AssessmentRule assessmentRule) {
    if (assessmentRule == null) {
      throw new IllegalArgumentException("判定ルールは必須です");
    }
    this.assessmentRule = assessmentRule;
  }

  /**
   * 卒業済み・処理済み番号・判定時点前なら状態をそのまま返す。
   * 補講中も次の判定機会では再判定できる（その時点は差し替え可能なルールに委譲）。
   * 再送防止には返却状態の保存が必要。同時更新の排他制御は保存担当 #11 で行う。
   */
  public StudentStatus assess(StudentStatus current, AssessmentContext context) {
    if (current == null || context == null) {
      throw new IllegalArgumentException("学生状態と判定情報は必須です");
    }
    if (current.enrollment() == EnrollmentStatus.GRADUATED
        || context.assessmentNumber() <= current.lastAssessmentNumber()
        || !assessmentRule.isDue(current, context)) {
      return current;
    }
    if (current.credits() >= REQUIRED_CREDITS) {
      if (context.graduationRank() <= 0) {
        throw new IllegalArgumentException("卒業時の順位は1以上で指定してください");
      }
      return new StudentStatus(current.credits(), current.repeatYears(), EnrollmentStatus.GRADUATED,
          context.graduationRank(), context.assessmentNumber());
    }
    return new StudentStatus(current.credits(), Math.incrementExact(current.repeatYears()),
        EnrollmentStatus.REMEDIAL, 0, context.assessmentNumber());
  }
}
