package oit.is.team7.isdev26team4.result;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import oit.is.team7.isdev26team4.result.GraduationService.AssessmentContext;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;
import oit.is.team7.isdev26team4.result.GraduationService.StudentStatus;
import oit.is.team7.isdev26team4.result.ResultController.ParticipantResult;
import oit.is.team7.isdev26team4.result.ResultController.ResultSnapshot;

@WebMvcTest(ResultController.class)
@AutoConfigureMockMvc(addFilters = false)
class ResultControllerTest {
  @Autowired private MockMvc mockMvc;

  private ParticipantResult graduate(String name, boolean cpu, int credits, int repeats, int rank) {
    StudentStatus enrolled = new StudentStatus(credits, repeats, EnrollmentStatus.ENROLLED, 0, 0);
    return new ParticipantResult(name, cpu,
        new GraduationService().assess(enrolled, new AssessmentContext(1, true, rank)));
  }

  @Test
  void rendersHumanAndCpuResultsInSuppliedRankOrder() throws Exception {
    ParticipantResult human = graduate("学生A", false, 126, 1, 2);
    ParticipantResult cpu = graduate("CPU1", true, 124, 0, 1);
    String html = mockMvc.perform(get("/result").sessionAttr(
            ResultController.RESULT_SESSION_ATTRIBUTE, new ResultSnapshot(List.of(human, cpu))))
        .andExpect(status().isOk()).andExpect(view().name("result"))
        .andExpect(model().attribute("completed", true))
        .andExpect(model().attribute("results", List.of(cpu, human)))
        .andExpect(content().string(containsString("<td>126</td>")))
        .andExpect(content().string(containsString("<td>124</td>")))
        .andExpect(content().string(containsString("<td>人間</td>")))
        .andExpect(content().string(containsString("<td>CPU</td>")))
        .andReturn().getResponse().getContentAsString();
    assertTrue(html.indexOf("CPU1") < html.indexOf("学生A"));
    assertTrue(html.contains("<td>2</td>\n          <td>学生A</td>"));
    assertTrue(html.contains("<td>126</td>\n          <td>1</td>"));
  }

  @Test
  void preservesTiedRanksAndDoesNotModifyInput() throws Exception {
    ParticipantResult first = graduate("学生A", false, 124, 0, 1);
    ParticipantResult second = graduate("CPU1", true, 128, 2, 1);
    List<ParticipantResult> input = new ArrayList<>(List.of(second, first));
    ResultSnapshot snapshot = new ResultSnapshot(input);
    input.clear();
    mockMvc.perform(get("/result").sessionAttr(ResultController.RESULT_SESSION_ATTRIBUTE, snapshot))
        .andExpect(status().isOk())
        .andExpect(model().attribute("results", List.of(second, first)));
    assertEquals(1, first.status().graduationRank());
    assertEquals(1, second.status().graduationRank());
    assertEquals(List.of(second, first), snapshot.participants());
  }

  @Test
  void missingOrEmptySnapshotShowsPendingWithoutResults() throws Exception {
    mockMvc.perform(get("/result")).andExpect(status().isOk())
        .andExpect(content().string(containsString("結果未確定")))
        .andExpect(content().string(not(containsString("<table"))));
    mockMvc.perform(get("/result").sessionAttr(ResultController.RESULT_SESSION_ATTRIBUTE,
            new ResultSnapshot(List.of())))
        .andExpect(model().attribute("completed", false))
        .andExpect(model().attribute("results", List.of()));
  }

  @Test
  void doesNotPublishPartialResultsWhileSomeoneIsEnrolledOrRemedial() throws Exception {
    for (EnrollmentStatus status : List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.REMEDIAL)) {
      ParticipantResult unfinished = new ParticipantResult("未卒業", true,
          new StudentStatus(123, 1, status, 0, 1));
      mockMvc.perform(get("/result").sessionAttr(ResultController.RESULT_SESSION_ATTRIBUTE,
              new ResultSnapshot(List.of(graduate("卒業済み", false, 124, 0, 1), unfinished))))
          .andExpect(status().isOk()).andExpect(model().attribute("completed", false))
          .andExpect(content().string(not(containsString("<table"))))
          .andExpect(content().string(not(containsString("卒業済み"))));
    }
  }

  @Test
  void escapesParticipantNamesInRenderedHtml() throws Exception {
    mockMvc.perform(get("/result").sessionAttr(ResultController.RESULT_SESSION_ATTRIBUTE,
            new ResultSnapshot(List.of(graduate("<script>alert(1)</script>", false, 124, 0, 1)))))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("&lt;script&gt;")))
        .andExpect(content().string(not(containsString("<script>"))));
  }

  @Test
  void resultIsScopedToSessionAndQueryParametersCannotSetResults() throws Exception {
    MockHttpSession completedSession = new MockHttpSession();
    completedSession.setAttribute(ResultController.RESULT_SESSION_ATTRIBUTE,
        new ResultSnapshot(List.of(graduate("学生A", false, 124, 0, 1))));
    mockMvc.perform(get("/result").session(completedSession))
        .andExpect(model().attribute("completed", true));
    mockMvc.perform(get("/result").session(new MockHttpSession())
            .param("graduationRank", "1").param("credits", "124"))
        .andExpect(model().attribute("completed", false));
  }

  @Test
  void rejectsInvalidSnapshotData() {
    assertThrows(IllegalArgumentException.class, () -> new ResultSnapshot(null));
    assertThrows(IllegalArgumentException.class,
        () -> new ResultSnapshot(java.util.Arrays.asList((ParticipantResult) null)));
    StudentStatus status = new StudentStatus(124, 0, EnrollmentStatus.GRADUATED, 1, 1);
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(" ", false, status));
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(null, false, status));
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult("学生A", false, null));
  }
}
