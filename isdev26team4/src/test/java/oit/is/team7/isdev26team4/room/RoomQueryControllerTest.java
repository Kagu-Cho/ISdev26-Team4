package oit.is.team7.isdev26team4.room;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RoomQueryController.class)
@AutoConfigureMockMvc(addFilters = false)
class RoomQueryControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  void enterShowsRoomNameAndAvailableSeats() throws Exception {
    mockMvc.perform(get("/Enter/12"))
        .andExpect(status().isOk())
        .andExpect(view().name("room"))
        .andExpect(content().string(containsString("Room(12)")))
        .andExpect(content().string(containsString("参加人数: <span>0</span>人")))
        .andExpect(content().string(containsString("空席: <span>4</span>席")));
  }

  @Test
  void refreshKeepsParticipantCountAndVacancyConsistent() throws Exception {
    String firstResponse = mockMvc.perform(get("/Enter/12"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    String refreshedResponse = mockMvc.perform(get("/Enter/12"))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    org.junit.jupiter.api.Assertions.assertEquals(firstResponse, refreshedResponse);
  }

  @Test
  void enterWithoutRoomNumberUsesTheDefaultRoom() throws Exception {
    mockMvc.perform(get("/Enter"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Room(1)")));
  }

  @Test
  void nonNumericRoomNumberIsRejected() throws Exception {
    mockMvc.perform(get("/Enter/not-a-number"))
        .andExpect(status().isBadRequest());
  }
}
