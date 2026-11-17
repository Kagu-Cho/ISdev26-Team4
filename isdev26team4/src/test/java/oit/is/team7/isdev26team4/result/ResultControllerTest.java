package oit.is.team7.isdev26team4.result;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;
import oit.is.team7.isdev26team4.result.GraduationService.AssessmentContext;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;
import oit.is.team7.isdev26team4.result.GraduationService.StudentStatus;
import oit.is.team7.isdev26team4.result.ResultController.*;

@WebMvcTest(ResultController.class)
class ResultControllerTest {
  private static final UUID GAME = UUID.fromString("00000000-0000-0000-0000-000000000001");
  private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");
  private static final String API = "/api/games/" + GAME + "/result";
  private static final String PAGE = "/games/" + GAME + "/result";
  @Autowired private MockMvc mockMvc;
  @MockitoBean private GameResultSource source;

  private ParticipantResult graduate(int number, String name, boolean cpu, int credits, int repeats, int rank) {
    StudentStatus input = new StudentStatus(credits, repeats, EnrollmentStatus.ENROLLED, 0, 0);
    return new ParticipantResult(new UUID(1, number), number, name, cpu,
        new GraduationService().assess(input, new AssessmentContext(1, true, rank)));
  }

  private GameResultState state(GameStatus status, List<ParticipantResult> players) {
    return new GameResultState(GAME, status, Set.of("viewer", "creator"), players);
  }

  private MockHttpServletRequestBuilder request(String path) {
    return get(path).with(user("viewer"));
  }

  @BeforeEach
  void prepareSavedState() {
    when(source.find(GAME)).thenReturn(Optional.of(state(GameStatus.FINISHED, List.of(
        graduate(1, "学生A", false, 126, 1, 2), graduate(2, "CPU1", true, 124, 0, 1)))));
    when(source.find(OTHER)).thenReturn(Optional.empty());
  }

  @Test
  void returnsSavedResultsWithoutLeakingAccessInformation() throws Exception {
    mockMvc.perform(request(API).accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.gameId").value(GAME.toString()))
        .andExpect(jsonPath("$.status").value("FINISHED"))
        .andExpect(jsonPath("$.players", hasSize(2)))
        .andExpect(jsonPath("$.players[0].displayName").value("CPU1"))
        .andExpect(jsonPath("$.players[0].cpu").value(true))
        .andExpect(jsonPath("$.players[0].credits").value(124))
        .andExpect(jsonPath("$.players[0].repeatYears").value(0))
        .andExpect(jsonPath("$.players[0].enrollmentStatus").value("GRADUATED"))
        .andExpect(jsonPath("$.players[0].graduationRank").value(1))
        .andExpect(jsonPath("$.players[1].displayName").value("学生A"))
        .andExpect(jsonPath("$.players[1].cpu").value(false))
        .andExpect(jsonPath("$.players[1].credits").value(126))
        .andExpect(jsonPath("$.players[1].repeatYears").value(1))
        .andExpect(jsonPath("$.players[1].graduationRank").value(2))
        .andExpect(jsonPath("$.allowedUsernames").doesNotExist());
    verify(source).find(GAME);
    verifyNoMoreInteractions(source);
  }

  @Test
  void rendersSameSavedResultsInActualTemplate() throws Exception {
    String html = mockMvc.perform(request(PAGE))
        .andExpect(status().isOk()).andExpect(view().name("result"))
        .andExpect(content().string(containsString("<td>人間</td>")))
        .andExpect(content().string(containsString("<td>CPU</td>")))
        .andExpect(content().string(containsString("<td>126</td>")))
        .andExpect(content().string(containsString("<td>124</td>")))
        .andReturn().getResponse().getContentAsString();
    assertTrue(html.indexOf("CPU1") < html.indexOf("学生A"));
    assertTrue(html.contains("<td>2</td>\n          <td>学生A</td>"));
    assertTrue(html.contains("<td>126</td>\n          <td>1</td>"));
    mockMvc.perform(request("/result").param("gameId", GAME.toString()))
        .andExpect(status().isOk()).andExpect(model().attribute("completed", true));
  }

  @Test
  void includesFourPlayersAndDoesNotRecalculatePersistedRanks() throws Exception {
    when(source.find(GAME)).thenReturn(Optional.of(state(GameStatus.FINISHED, List.of(
        graduate(1, "人間1", false, 124, 2, 4), graduate(2, "CPU1", true, 124, 1, 2),
        graduate(3, "人間2", false, 130, 0, 3), graduate(4, "CPU2", true, 128, 0, 1)))));
    mockMvc.perform(request(API)).andExpect(status().isOk())
        .andExpect(jsonPath("$.players", hasSize(4)))
        .andExpect(jsonPath("$.players[*].graduationRank", contains(1, 2, 3, 4)))
        .andExpect(jsonPath("$.players[*].displayName", contains("CPU2", "CPU1", "人間2", "人間1")));
  }

  @Test
  void startAuthorityMayReadWithoutBeingAPlayer() throws Exception {
    mockMvc.perform(get(API).with(user("creator"))).andExpect(status().isOk());
  }

  @Test
  void outsiderCannotReadOrForgeIdentityThroughParameters() throws Exception {
    for (String path : List.of(API, PAGE)) {
      mockMvc.perform(get(path).with(user("outsider")).param("participantId", "viewer")
              .param("username", "creator"))
          .andExpect(status().isForbidden())
          .andExpect(content().string(not(containsString("学生A"))));
    }
  }

  @Test
  void anonymousRequestsRequireAuthentication() throws Exception {
    mockMvc.perform(get(API).accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    mockMvc.perform(get(PAGE).accept(MediaType.TEXT_HTML)).andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
    verifyNoInteractions(source);
  }

  @Test
  void unknownGameReturns404AndInvalidIdentifierReturns400() throws Exception {
    mockMvc.perform(request("/api/games/" + OTHER + "/result")).andExpect(status().isNotFound());
    mockMvc.perform(request("/api/games/not-a-uuid/result")).andExpect(status().isBadRequest());
    mockMvc.perform(request("/result").param("gameId", "not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void waitingPlayingAndEmptyGamesReturn409WithoutPartialResults() throws Exception {
    List<ParticipantResult> graduates = List.of(graduate(1, "学生A", false, 124, 0, 1));
    for (GameResultState pending : List.of(state(GameStatus.WAITING, graduates),
        state(GameStatus.PLAYING, graduates), state(GameStatus.FINISHED, List.of()))) {
      when(source.find(GAME)).thenReturn(Optional.of(pending));
      mockMvc.perform(request(API)).andExpect(status().isConflict())
          .andExpect(content().string(not(containsString("学生A"))));
      mockMvc.perform(request(PAGE)).andExpect(status().isConflict())
          .andExpect(content().string(containsString("結果未確定")))
          .andExpect(content().string(not(containsString("<table"))));
    }
  }

  @Test
  void unfinishedStudentsPreventResultsEvenWhenGameIsMarkedFinished() throws Exception {
    for (EnrollmentStatus status : List.of(EnrollmentStatus.ENROLLED, EnrollmentStatus.REMEDIAL)) {
      ParticipantResult unfinished = new ParticipantResult(new UUID(1, 2), 2, "未卒業", true,
          new StudentStatus(123, 1, status, 0, 1));
      when(source.find(GAME)).thenReturn(Optional.of(state(GameStatus.FINISHED,
          List.of(graduate(1, "卒業済み", false, 124, 0, 1), unfinished))));
      mockMvc.perform(request(API)).andExpect(status().isConflict());
      mockMvc.perform(request(PAGE)).andExpect(status().isConflict())
          .andExpect(content().string(not(containsString("卒業済み"))));
    }
  }

  @Test
  void checksPermissionsBeforeCompletion() throws Exception {
    when(source.find(GAME)).thenReturn(Optional.of(state(GameStatus.PLAYING, List.of())));
    mockMvc.perform(get(API).with(user("outsider"))).andExpect(status().isForbidden());
  }

  @Test
  void readsLatestStateInsteadOfOldSessionOrQueryValues() throws Exception {
    GameResultState completed = state(GameStatus.FINISHED,
        List.of(graduate(1, "学生A", false, 124, 0, 1)));
    when(source.find(GAME)).thenReturn(Optional.of(completed), Optional.of(state(GameStatus.PLAYING, List.of())));
    mockMvc.perform(request(API)).andExpect(status().isOk());
    mockMvc.perform(request(API).sessionAttr("resultSnapshot", completed)
            .param("graduationRank", "1").param("credits", "124"))
        .andExpect(status().isConflict());
    verify(source, times(2)).find(GAME);
  }

  @Test
  void rejectsSourceReturningAnotherGame() throws Exception {
    when(source.find(GAME)).thenReturn(Optional.of(
        new GameResultState(OTHER, GameStatus.FINISHED, Set.of("viewer"), List.of())));
    mockMvc.perform(request(API)).andExpect(status().isServiceUnavailable());
  }

  @Test
  void unconfiguredStorageDoesNotPretendToProvideResults() {
    @SuppressWarnings("unchecked") ObjectProvider<GameResultSource> unavailable = mock(ObjectProvider.class);
    ResultController controller = new ResultController(unavailable);
    ResponseStatusException exception = assertThrows(ResponseStatusException.class,
        () -> controller.result(GAME, () -> "viewer"));
    assertEquals(503, exception.getStatusCode().value());
  }

  @Test
  void rootPageAllowsGameSelectionWithoutDummyResults() throws Exception {
    mockMvc.perform(request("/result")).andExpect(status().isOk())
        .andExpect(content().string(containsString("結果を確認する試合ID")))
        .andExpect(content().string(not(containsString("<table"))));
    verifyNoInteractions(source);
  }

  @Test
  void escapesNamesAndDefensivelyCopiesSavedState() throws Exception {
    ParticipantResult participant = graduate(1, "<script>alert(1)</script>", false, 124, 0, 1);
    List<ParticipantResult> players = new ArrayList<>(List.of(participant));
    Set<String> users = new HashSet<>(Set.of("viewer"));
    GameResultState saved = new GameResultState(GAME, GameStatus.FINISHED, users, players);
    players.clear(); users.clear();
    when(source.find(GAME)).thenReturn(Optional.of(saved));
    mockMvc.perform(request(PAGE)).andExpect(status().isOk())
        .andExpect(content().string(containsString("&lt;script&gt;")))
        .andExpect(content().string(not(containsString("<script>"))));
    assertEquals(List.of(participant), saved.players());
    assertEquals(Set.of("viewer"), saved.allowedUsernames());
  }

  @Test
  void repeatedReadsKeepRanksAndDoNotMutateSavedState() throws Exception {
    GameResultState saved = state(GameStatus.FINISHED,
        List.of(graduate(1, "学生A", false, 124, 1, 2), graduate(2, "CPU1", true, 126, 0, 1)));
    when(source.find(GAME)).thenReturn(Optional.of(saved));
    String first = mockMvc.perform(request(API)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String second = mockMvc.perform(request(API)).andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertEquals(first, second);
    assertEquals(2, saved.players().get(0).status().graduationRank());
    assertEquals(1, saved.players().get(1).status().graduationRank());
    verify(source, times(2)).find(GAME);
    verifyNoMoreInteractions(source);
  }

  @Test
  void rejectsInvalidParticipantsAndDuplicateIdentities() {
    ParticipantResult valid = graduate(1, "学生A", false, 124, 0, 1);
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(null, 1, "A", false, valid.status()));
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(UUID.randomUUID(), 0, "A", false, valid.status()));
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(UUID.randomUUID(), 5, "A", false, valid.status()));
    assertThrows(IllegalArgumentException.class, () -> new ParticipantResult(UUID.randomUUID(), 1, " ", false, valid.status()));
    assertThrows(IllegalArgumentException.class, () -> state(GameStatus.FINISHED, List.of(valid, valid)));
    assertThrows(IllegalArgumentException.class, () -> new GameResultState(null, GameStatus.FINISHED, Set.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new GameResultState(GAME, null, Set.of(), List.of()));
    assertThrows(IllegalArgumentException.class, () -> new GameResultState(GAME, GameStatus.FINISHED, null, List.of()));
    assertThrows(IllegalArgumentException.class, () -> state(GameStatus.FINISHED, null));
  }
}
