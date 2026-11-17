package oit.is.team7.isdev26team4.exam;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.random.RandomGenerator;

import org.springframework.stereotype.Service;

/**
 * 半期末の過去問入手判定・科目別合否・獲得単位を計算する（ゲームルール共有仕様 4.2・4.3）。
 * 判定を行う月の決定（#12）、卒業・留年判定（#10）、状態の保存（#11）は扱わない。
 * 入力を変更せず、判定結果を新しい値として返す。
 */
@Service
public class SemesterExamService {

  public static final int COURSES_PER_SEMESTER = 8;
  public static final int CREDITS_PER_COURSE = 2;
  // 確率はパーセントの整数で持ち、最後に100で割る（3 × 0.10 が 0.30000000000000004 になる誤差を避ける）
  public static final int PAST_EXAM_PERCENT_PER_CONNECTION = 10;
  public static final int MAX_PAST_EXAM_PERCENT = 100;
  public static final int BASE_PASS_PERCENT = 50;
  public static final int PAST_EXAM_BONUS_PERCENT = 20;

  /** 科目ごとの判定結果。courseNumber は1〜8。 */
  public record CourseResult(int courseNumber, boolean pastExamObtained, boolean passed) {
  }

  /** 半期の判定結果。totalCredits は判定前の単位に earnedCredits を加えた値。 */
  public record SemesterResult(List<CourseResult> courses, int earnedCredits, int totalCredits) {
    public SemesterResult {
      courses = List.copyOf(courses);
    }
  }

  private final RandomGenerator random;

  public SemesterExamService() {
    this(new Random());
  }

  /** テストで乱数列を固定するためのコンストラクタ。 */
  public SemesterExamService(RandomGenerator random) {
    if (random == null) {
      throw new IllegalArgumentException("乱数生成器は必須です");
    }
    this.random = random;
  }

  /** 過去問入手率 = min(100%, 人脈 × 10%)。 */
  public double pastExamRate(int connections) {
    if (connections < 0) {
      throw new IllegalArgumentException("人脈は0以上です");
    }
    // 人脈が大きくても掛け算で桁あふれしないよう、上限に達する人脈で先に打ち切る
    int capped = Math.min(connections, MAX_PAST_EXAM_PERCENT / PAST_EXAM_PERCENT_PER_CONNECTION);
    return Math.min(MAX_PAST_EXAM_PERCENT, capped * PAST_EXAM_PERCENT_PER_CONNECTION) / 100.0;
  }

  /** 基礎合格率50%。過去問を入手した科目は+20ポイントで70%。学力には依存しない。 */
  public double passRate(boolean pastExamObtained) {
    int percent = pastExamObtained ? BASE_PASS_PERCENT + PAST_EXAM_BONUS_PERCENT : BASE_PASS_PERCENT;
    return percent / 100.0;
  }

  /**
   * 仕様の判定順（履修科目の確定 → 過去問入手判定 → 科目別合否 → 単位加算）に従う。
   * 乱数は先に8科目分の過去問判定、次に8科目分の合否判定の順で1回ずつ使う。
   * 乱数が確率未満なら成功とする。
   */
  public SemesterResult settle(int connections, int currentCredits) {
    if (currentCredits < 0) {
      throw new IllegalArgumentException("単位は0以上です");
    }
    double examRate = pastExamRate(connections);

    boolean[] pastExams = new boolean[COURSES_PER_SEMESTER];
    for (int i = 0; i < COURSES_PER_SEMESTER; i++) {
      pastExams[i] = random.nextDouble() < examRate;
    }

    List<CourseResult> courses = new ArrayList<>();
    int earned = 0;
    for (int i = 0; i < COURSES_PER_SEMESTER; i++) {
      boolean passed = random.nextDouble() < passRate(pastExams[i]);
      if (passed) {
        earned += CREDITS_PER_COURSE;
      }
      courses.add(new CourseResult(i + 1, pastExams[i], passed));
    }
    return new SemesterResult(courses, earned, currentCredits + earned);
  }
}
