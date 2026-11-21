package oit.is.team7.isdev26team4.room;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
@AutoConfigureTestDatabase
class RoomParticipationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private RoomRepository repository;

  @BeforeEach
  void clearParticipants() {
    jdbcTemplate.update("DELETE FROM room_participant");
  }

  @Test
  void loggedInParticipantIsSavedAndShownInRoom() throws Exception {
    mockMvc.perform(get("/Enter/12"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("参加する")));

    mockMvc.perform(post("/Enter/12/participants").principal(principal("alice")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/Enter/12"));

    mockMvc.perform(get("/Enter/12"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("参加人数: <span>1</span>人")))
        .andExpect(content().string(containsString("空席: <span>3</span>席")));
  }

  @Test
  void repeatedParticipationDoesNotCreateDuplicate() throws Exception {
    mockMvc.perform(post("/Enter/12/participants").principal(principal("alice")))
        .andExpect(status().is3xxRedirection());
    mockMvc.perform(post("/Enter/12/participants").principal(principal("alice")))
        .andExpect(status().is3xxRedirection());

    assertEquals(1, repository.countParticipants("12"));
  }

  @Test
  void simultaneousParticipationDoesNotCreateDuplicateOrReturnServerError() throws Exception {
    java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newFixedThreadPool(2);

    java.util.List<java.util.concurrent.Future<org.springframework.test.web.servlet.MvcResult>> results = java.util.List
        .of("alice", "alice").stream()
        .map(name -> executor.submit(() -> mockMvc.perform(post("/Enter/12/participants")
            .principal(principal(name)))
            .andReturn()))
        .toList();

    for (java.util.concurrent.Future<org.springframework.test.web.servlet.MvcResult> result : results) {
      int responseStatus = result.get().getResponse().getStatus();
      org.junit.jupiter.api.Assertions.assertTrue(responseStatus == 302 || responseStatus == 409);
    }

    executor.shutdown();
    assertEquals(1, repository.countParticipants("12"));
  }

  @Test
  void loginIsRequiredToJoin() throws Exception {
    mockMvc.perform(post("/Enter/12/participants"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void fifthParticipantIsRejectedAndExistingParticipantsRemain() throws Exception {
    for (int number = 1; number <= 4; number++) {
      mockMvc.perform(post("/Enter/12/participants")
          .principal(principal("participant-" + number)))
          .andExpect(status().is3xxRedirection());
    }

    mockMvc.perform(post("/Enter/12/participants").principal(principal("participant-5")))
        .andExpect(status().isConflict());

    assertEquals(4, repository.countParticipants("12"));
  }

  private Principal principal(String name) {
    return () -> name;
  }
}
