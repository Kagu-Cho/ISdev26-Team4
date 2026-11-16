package oit.is.team7.isdev26team4.event;

import static oit.is.team7.isdev26team4.event.MonthlyActionService.*;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import oit.is.team7.isdev26team4.event.MonthlyActionService.FeeResult;
import oit.is.team7.isdev26team4.event.MonthlyActionService.MonthlyAction;
import oit.is.team7.isdev26team4.event.MonthlyActionService.MonthlyResult;
import oit.is.team7.isdev26team4.event.MonthlyActionService.StudentStatus;

class MonthlyActionServiceTest {

  private final MonthlyActionService service = new MonthlyActionService();

  // AC1
  @Test
  void 勉強を選ぶと学力が確定済みの増分だけ上がり他は変わらない() {
    StudentStatus before = new StudentStatus(50, 3, 10000, false);
    MonthlyResult result = service.applyMonthlyAction(before, MonthlyAction.STUDY);
    assertEquals(new StudentStatus(50 + STUDY_GAIN, 3, 10000, false), result.status());
    assertFalse(result.circleRejected());
  }

  // AC1 境界値
  @Test
  void 勉強で学力が上限を超えない() {
    StudentStatus before = new StudentStatus(ACADEMIC_MAX - 1, 0, 10000, false);
    StudentStatus after = service.applyMonthlyAction(before, MonthlyAction.STUDY).status();
    assertEquals(ACADEMIC_MAX, after.academic());
  }

  // AC2
  @Test
  void サークル参加者がサークルを選ぶと部費なしで人脈を得る() {
    StudentStatus before = new StudentStatus(50, 2, 5000, true);
    MonthlyResult result = service.applyMonthlyAction(before, MonthlyAction.CIRCLE);
    assertEquals(new StudentStatus(50 - FORGET_LOSS, 2 + CIRCLE_CONNECTION_GAIN, 5000, true),
        result.status());
    assertFalse(result.circleRejected());
  }

  // AC2
  @Test
  void 未参加者がサークルを選ぶと部費を払って入部し人脈を得る() {
    StudentStatus before = new StudentStatus(50, 0, 10000, false);
    MonthlyResult result = service.applyMonthlyAction(before, MonthlyAction.CIRCLE);
    assertEquals(new StudentStatus(50 - FORGET_LOSS, CIRCLE_CONNECTION_GAIN,
        10000 - ANNUAL_CIRCLE_FEE, true), result.status());
    assertFalse(result.circleRejected());
  }

  // AC2 境界値: 所持金がちょうど部費
  @Test
  void 所持金がちょうど部費なら入部できる() {
    StudentStatus before = new StudentStatus(50, 0, ANNUAL_CIRCLE_FEE, false);
    MonthlyResult result = service.applyMonthlyAction(before, MonthlyAction.CIRCLE);
    assertFalse(result.circleRejected());
    assertEquals(new StudentStatus(50 - FORGET_LOSS, CIRCLE_CONNECTION_GAIN, 0, true),
        result.status());
  }

  // AC2 失敗例
  @Test
  void 部費が足りない未参加者は入部できず人脈も所持金も変わらない() {
    StudentStatus before = new StudentStatus(50, 0, ANNUAL_CIRCLE_FEE - 1, false);
    MonthlyResult result = service.applyMonthlyAction(before, MonthlyAction.CIRCLE);
    assertTrue(result.circleRejected());
    assertEquals(new StudentStatus(50 - FORGET_LOSS, 0, ANNUAL_CIRCLE_FEE - 1, false),
        result.status());
  }

  // AC3
  @Test
  void アルバイトを選ぶと所持金が20000円増え忘却が適用される() {
    StudentStatus before = new StudentStatus(50, 1, 10000, false);
    StudentStatus after = service.applyMonthlyAction(before, MonthlyAction.PART_TIME).status();
    assertEquals(new StudentStatus(50 - FORGET_LOSS, 1, 30000, false), after);
  }

  // AC3 境界値
  @Test
  void 忘却で学力が下限を下回らない() {
    StudentStatus before = new StudentStatus(ACADEMIC_MIN + 1, 0, 0, false);
    StudentStatus after = service.applyMonthlyAction(before, MonthlyAction.PART_TIME).status();
    assertEquals(ACADEMIC_MIN, after.academic());
  }

  // AC4
  @Test
  void 年度替わりにサークル参加者から年部費3000円を徴収する() {
    StudentStatus before = new StudentStatus(50, 4, 10000, true);
    FeeResult result = service.collectAnnualFee(before);
    assertTrue(result.feePaid());
    assertFalse(result.withdrawn());
    assertEquals(new StudentStatus(50, 4, 7000, true), result.status());
  }

  // AC4
  @Test
  void 年度替わりにサークル未参加者からは徴収しない() {
    StudentStatus before = new StudentStatus(50, 0, 10000, false);
    FeeResult result = service.collectAnnualFee(before);
    assertFalse(result.feePaid());
    assertFalse(result.withdrawn());
    assertEquals(before, result.status());
  }

  // AC4 失敗例
  @Test
  void 年部費を払えない参加者は徴収されず退部し人脈は残る() {
    StudentStatus before = new StudentStatus(50, 4, ANNUAL_CIRCLE_FEE - 1, true);
    FeeResult result = service.collectAnnualFee(before);
    assertFalse(result.feePaid());
    assertTrue(result.withdrawn());
    assertEquals(new StudentStatus(50, 4, ANNUAL_CIRCLE_FEE - 1, false), result.status());
  }

  @Test
  void 状態や行動がnullなら例外になる() {
    StudentStatus s = new StudentStatus(50, 0, 0, false);
    assertThrows(IllegalArgumentException.class, () -> service.applyMonthlyAction(null, MonthlyAction.STUDY));
    assertThrows(IllegalArgumentException.class, () -> service.applyMonthlyAction(s, null));
    assertThrows(IllegalArgumentException.class, () -> service.collectAnnualFee(null));
  }
}
