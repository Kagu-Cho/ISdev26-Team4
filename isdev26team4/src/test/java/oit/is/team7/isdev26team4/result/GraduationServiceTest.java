package oit.is.team7.isdev26team4.result;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import oit.is.team7.isdev26team4.result.GraduationService.AssessmentContext;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;
import oit.is.team7.isdev26team4.result.GraduationService.StudentStatus;

class GraduationServiceTest {
  private final GraduationService service = new GraduationService();

  private StudentStatus enrolled(int credits) {
    return new StudentStatus(credits, 0, EnrollmentStatus.ENROLLED, 0, 0);
  }

  @Test
  void graduatesWith124OrMoreCreditsWithoutChangingInput() {
    for (int credits : new int[] {124, 125, Integer.MAX_VALUE}) {
      StudentStatus input = enrolled(credits);
      assertEquals(new StudentStatus(credits, 0, EnrollmentStatus.GRADUATED, 2, 1),
          service.assess(input, new AssessmentContext(1, true, 2)));
      assertEquals(enrolled(credits), input);
    }
  }

  @Test
  void movesToRemedialWith123OrFewerCredits() {
    for (int credits : new int[] {0, 122, 123}) {
      assertEquals(new StudentStatus(credits, 1, EnrollmentStatus.REMEDIAL, 0, 1),
          service.assess(enrolled(credits), new AssessmentContext(1, true, 0)));
    }
  }

  @Test
  void doesNotAssessBeforeDueOrConsumeAssessmentNumber() {
    StudentStatus current = enrolled(124);
    StudentStatus early = service.assess(current, new AssessmentContext(1, false, 0));
    assertSame(current, early);
    assertEquals(EnrollmentStatus.GRADUATED,
        service.assess(early, new AssessmentContext(1, true, 1)).enrollment());
  }

  @Test
  void doesNotChangeGraduatedStudentOnAnotherAssessment() {
    StudentStatus graduated = service.assess(enrolled(124), new AssessmentContext(1, true, 2));
    assertSame(graduated, service.assess(graduated, new AssessmentContext(2, true, 3)));
  }

  @Test
  void repeatedOrOlderAssessmentDoesNotAddAnotherRepeatYear() {
    StudentStatus remedial = service.assess(enrolled(123), new AssessmentContext(2, true, 0));
    assertSame(remedial, service.assess(remedial, new AssessmentContext(2, true, 0)));
    assertSame(remedial, service.assess(remedial, new AssessmentContext(1, true, 0)));
    assertEquals(2, service.assess(remedial, new AssessmentContext(3, true, 0)).repeatYears());
  }

  @Test
  void remedialStudentCanGraduateAtNextAssessmentAfterEarningCredits() {
    StudentStatus remedial = new StudentStatus(124, 2, EnrollmentStatus.REMEDIAL, 0, 3);
    assertEquals(new StudentStatus(124, 2, EnrollmentStatus.GRADUATED, 4, 4),
        service.assess(remedial, new AssessmentContext(4, true, 4)));
  }

  @Test
  void assessmentTimingCanBeReplacedWithoutChangingGraduationLogic() {
    GraduationService custom = new GraduationService((student, context) -> context.assessmentNumber() >= 5);
    StudentStatus current = enrolled(124);
    assertSame(current, custom.assess(current, new AssessmentContext(4, true, 1)));
    assertEquals(EnrollmentStatus.GRADUATED,
        custom.assess(current, new AssessmentContext(5, false, 1)).enrollment());
  }

  @Test
  void rejectsMissingOrInvalidInputs() {
    assertThrows(IllegalArgumentException.class, () -> new GraduationService(null));
    assertThrows(IllegalArgumentException.class, () -> service.assess(null, new AssessmentContext(1, true, 1)));
    assertThrows(IllegalArgumentException.class, () -> service.assess(enrolled(124), null));
    assertThrows(IllegalArgumentException.class, () -> enrolled(-1));
    assertThrows(IllegalArgumentException.class, () -> new StudentStatus(0, -1, EnrollmentStatus.ENROLLED, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StudentStatus(0, 0, null, 0, 0));
    assertThrows(IllegalArgumentException.class, () -> new StudentStatus(124, 0, EnrollmentStatus.GRADUATED, 0, 1));
    assertThrows(IllegalArgumentException.class, () -> new StudentStatus(0, 0, EnrollmentStatus.ENROLLED, 1, 0));
    assertThrows(IllegalArgumentException.class, () -> new StudentStatus(0, 0, EnrollmentStatus.ENROLLED, 0, -1));
    assertThrows(IllegalArgumentException.class, () -> new AssessmentContext(0, true, 1));
    assertThrows(IllegalArgumentException.class, () -> new AssessmentContext(1, true, -1));
    assertThrows(IllegalArgumentException.class, () -> service.assess(enrolled(124), new AssessmentContext(1, true, 0)));
  }

  @Test
  void rejectsRepeatYearOverflow() {
    StudentStatus current = new StudentStatus(123, Integer.MAX_VALUE, EnrollmentStatus.REMEDIAL, 0, 1);
    assertThrows(ArithmeticException.class, () -> service.assess(current, new AssessmentContext(2, true, 0)));
  }
}
