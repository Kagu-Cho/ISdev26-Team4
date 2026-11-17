package oit.is.team7.isdev26team4.exam;

import static oit.is.team7.isdev26team4.exam.SemesterExamService.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.Test;

import oit.is.team7.isdev26team4.exam.SemesterExamService.CourseResult;
import oit.is.team7.isdev26team4.exam.SemesterExamService.SemesterResult;

class SemesterExamServiceTest {

  /** 指定した順に nextDouble を返す乱数。足りなくなったら失敗させる。 */
  private static final class FixedRandom implements RandomGenerator {
    private final Deque<Double> values = new ArrayDeque<>();

    FixedRandom(double... values) {
      Arrays.stream(values).forEach(this.values::add);
    }

    @Override
    public double nextDouble() {
      if (values.isEmpty()) {
        throw new IllegalStateException("用意した乱数を使い切りました");
      }
      return values.poll();
    }

    @Override
    public long nextLong() {
      throw new UnsupportedOperationException();
    }

    int remaining() {
      return values.size();
    }
  }

  /** 過去問判定8回分と合否判定8回分を連結する。 */
  private static double[] draws(double[] pastExamDraws, double[] passDraws) {
    double[] all = new double[16];
    System.arraycopy(pastExamDraws, 0, all, 0, 8);
    System.arraycopy(passDraws, 0, all, 8, 8);
    return all;
  }

  private static double[] repeat(double value) {
    double[] a = new double[8];
    Arrays.fill(a, value);
    return a;
  }

  private final SemesterExamService service = new SemesterExamService();

  // AC1
  @Test
  void 過去問入手率は人脈1につき10パーセント() {
    assertEquals(0.0, service.pastExamRate(0), 1e-9);
    assertEquals(0.3, service.pastExamRate(3), 1e-9);
    assertEquals(0.9, service.pastExamRate(9), 1e-9);
  }

  // AC1 境界値
  @Test
  void 過去問入手率は100パーセントで頭打ちになる() {
    assertEquals(1.0, service.pastExamRate(10), 1e-9);
    assertEquals(1.0, service.pastExamRate(25), 1e-9);
    assertEquals(1.0, service.pastExamRate(Integer.MAX_VALUE), 1e-9);
  }

  // AC1 失敗例
  @Test
  void 人脈が負なら例外になる() {
    assertThrows(IllegalArgumentException.class, () -> service.pastExamRate(-1));
  }

  // AC2
  @Test
  void 過去問を入手した科目の合格率は未入手より20ポイント高い() {
    assertEquals(0.5, service.passRate(false), 1e-9);
    assertEquals(0.7, service.passRate(true), 1e-9);
    assertTrue(service.passRate(true) > service.passRate(false));
  }

  // AC2・AC4: 同じ乱数0.6で、過去問ありは合格・なしは不合格
  @Test
  void 同じ乱数でも過去問を入手した科目だけが合格する() {
    // 人脈5（入手率50%）: 科目1〜4は過去問入手、5〜8は未入手
    double[] exam = { 0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8 };
    FixedRandom random = new FixedRandom(draws(exam, repeat(0.6)));
    SemesterResult result = new SemesterExamService(random).settle(5, 0);

    for (CourseResult c : result.courses()) {
      boolean expected = c.courseNumber() <= 4;
      assertEquals(expected, c.pastExamObtained(), "過去問 科目" + c.courseNumber());
      assertEquals(expected, c.passed(), "合否 科目" + c.courseNumber());
    }
    assertEquals(8, result.earnedCredits());
    assertEquals(0, random.remaining());
  }

  // AC3
  @Test
  void 合格1科目につき2単位を判定前の単位に加算する() {
    // 過去問なし（人脈0）。合格率50%: 0.49は合格、0.5は不合格
    double[] pass = { 0.49, 0.5, 0.0, 0.99, 0.49, 0.5, 0.49, 0.5 };
    SemesterResult result = new SemesterExamService(new FixedRandom(draws(repeat(0.0), pass)))
        .settle(0, 30);

    assertEquals(8, result.courses().size());
    assertEquals(4 * CREDITS_PER_COURSE, result.earnedCredits());
    assertEquals(30 + 8, result.totalCredits());
  }

  // AC3 境界値: 全科目合格で最大16単位
  @Test
  void 全科目合格なら1半期16単位を得る() {
    SemesterResult result = new SemesterExamService(new FixedRandom(draws(repeat(0.0), repeat(0.0))))
        .settle(10, 108);
    assertEquals(16, result.earnedCredits());
    assertEquals(124, result.totalCredits());
  }

  // AC3 失敗例: 全科目不合格なら単位は増えない
  @Test
  void 全科目不合格なら単位は増えない() {
    SemesterResult result = new SemesterExamService(
        new FixedRandom(draws(repeat(0.99), repeat(0.99)))).settle(3, 20);
    assertEquals(0, result.earnedCredits());
    assertEquals(20, result.totalCredits());
    assertTrue(result.courses().stream().noneMatch(CourseResult::passed));
  }

  // AC4 境界値: 入手率0%は乱数0.0でも入手できない
  @Test
  void 人脈0なら乱数が0でも過去問を入手できない() {
    SemesterResult result = new SemesterExamService(new FixedRandom(draws(repeat(0.0), repeat(0.99))))
        .settle(0, 0);
    assertTrue(result.courses().stream().noneMatch(CourseResult::pastExamObtained));
  }

  // AC4 境界値: 入手率100%は乱数が1に近くても必ず入手する
  @Test
  void 人脈10以上なら乱数が1に近くても必ず過去問を入手する() {
    SemesterResult result = new SemesterExamService(
        new FixedRandom(draws(repeat(0.9999999), repeat(0.99)))).settle(12, 0);
    assertTrue(result.courses().stream().allMatch(CourseResult::pastExamObtained));
  }

  // AC4 境界値: 乱数が確率と等しいときは失敗（確率未満のみ成功）
  @Test
  void 乱数が確率と等しいときは失敗になる() {
    SemesterResult result = new SemesterExamService(new FixedRandom(draws(repeat(0.3), repeat(0.7))))
        .settle(3, 0);
    assertTrue(result.courses().stream().noneMatch(CourseResult::pastExamObtained));
    assertTrue(result.courses().stream().noneMatch(CourseResult::passed));
  }

  @Test
  void 単位が負や乱数生成器がnullなら例外になる() {
    assertThrows(IllegalArgumentException.class, () -> service.settle(0, -1));
    assertThrows(IllegalArgumentException.class, () -> new SemesterExamService(null));
  }
}
