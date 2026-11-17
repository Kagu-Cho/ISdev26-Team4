package oit.is.team7.isdev26team4.game;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(BoardController.class)
@AutoConfigureMockMvc(addFilters = false)
class BoardControllerTest {

  private static final int BOARD_WIDTH = 1200;
  private static final int BOARD_HEIGHT = 850;
  private static final Pattern SQUARE_NODE_PATTERN = Pattern.compile("class=\"square-node[^\"]*\"");

  @Autowired
  private MockMvc mockMvc;

  @Test
  @DisplayName("AC1: 盤面を開いたとき100マス以上のマップと4人の駒、および接続線データが表示される")
  void testBoardDisplaysAtLeast100SquaresAnd4Players() throws Exception {
    MvcResult result = mockMvc.perform(get("/game"))
        .andExpect(status().isOk())
        .andExpect(view().name("game"))
        .andExpect(model().attributeExists("squares", "trackPoints", "players", "currentTurnPlayer"))
        .andExpect(model().attribute("squares", hasSize(100)))
        .andExpect(model().attribute("players", hasSize(4)))
        .andExpect(content().string(containsString("全 <span>100</span> マス")))
        .andExpect(content().string(containsString("data-square-id=\"1\"")))
        .andExpect(content().string(containsString("data-square-id=\"100\"")))
        .andExpect(content().string(containsString("<polyline")))
        .andReturn();

    @SuppressWarnings("unchecked")
    List<BoardController.Square> squares =
        (List<BoardController.Square>) result.getModelAndView().getModel().get("squares");
    assertNotNull(squares);
    assertEquals(100, squares.size());
    for (int i = 0; i < squares.size(); i++) {
      BoardController.Square square = squares.get(i);
      assertEquals(i + 1, square.id());
      assertTrue(square.x() > 0 && square.x() < BOARD_WIDTH,
          "square x out of board range: " + square);
      assertTrue(square.y() > 0 && square.y() < BOARD_HEIGHT,
          "square y out of board range: " + square);
    }

    String trackPoints = (String) result.getModelAndView().getModel().get("trackPoints");
    assertNotNull(trackPoints);
    String[] points = trackPoints.trim().split("\\s+");
    assertEquals(100, points.length);
    for (String point : points) {
      assertTrue(point.matches("\\d+,\\d+"), "invalid point format: " + point);
    }

    String html = result.getResponse().getContentAsString();
    assertEquals(100, countMatches(SQUARE_NODE_PATTERN, html));
  }

  @Test
  @DisplayName("AC2: 異なる位置と手番を与えたとき駒の位置と手番表示が反映される")
  void testBoardReflectsDifferentPositionsAndTurn() throws Exception {
    MvcResult result = mockMvc.perform(get("/game")
            .param("p1Pos", "15")
            .param("p2Pos", "30")
            .param("p3Pos", "55")
            .param("p4Pos", "80")
            .param("turnPlayer", "3"))
        .andExpect(status().isOk())
        .andExpect(view().name("game"))
        .andExpect(model().attributeExists("currentTurnPlayer"))
        .andExpect(content().string(containsString("<span class=\"turn-player-name\">プレイヤー3</span>")))
        .andReturn();

    BoardController.Player currentTurn =
        (BoardController.Player) result.getModelAndView().getModel().get("currentTurnPlayer");
    assertNotNull(currentTurn);
    assertEquals(3, currentTurn.id());
    assertEquals("プレイヤー3", currentTurn.name());
    assertTrue(currentTurn.isTurn());

    @SuppressWarnings("unchecked")
    List<BoardController.Player> players =
        (List<BoardController.Player>) result.getModelAndView().getModel().get("players");
    assertNotNull(players);
    assertEquals(15, players.get(0).currentSquareId());
    assertEquals(30, players.get(1).currentSquareId());
    assertEquals(55, players.get(2).currentSquareId());
    assertEquals(80, players.get(3).currentSquareId());
    assertTrue(players.get(2).isTurn());
    assertFalse(players.get(0).isTurn());
  }

  @Test
  @DisplayName("範囲外の位置・手番パラメータが安全に範囲内へ丸められ表示が整合する")
  void testOutOfRangeParametersAreClampedConsistently() throws Exception {
    MvcResult upperResult = mockMvc.perform(get("/game")
            .param("p1Pos", "0")
            .param("p2Pos", "150")
            .param("p3Pos", "-5")
            .param("p4Pos", "101")
            .param("turnPlayer", "5"))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("currentTurnPlayer"))
        .andExpect(content().string(containsString("<span class=\"turn-player-name\">プレイヤー4</span>")))
        .andReturn();

    BoardController.Player upperTurn =
        (BoardController.Player) upperResult.getModelAndView().getModel().get("currentTurnPlayer");
    assertNotNull(upperTurn);
    assertEquals(4, upperTurn.id());
    assertTrue(upperTurn.isTurn());

    @SuppressWarnings("unchecked")
    List<BoardController.Player> upperPlayers =
        (List<BoardController.Player>) upperResult.getModelAndView().getModel().get("players");
    assertEquals(1, upperPlayers.get(0).currentSquareId());
    assertEquals(100, upperPlayers.get(1).currentSquareId());
    assertEquals(1, upperPlayers.get(2).currentSquareId());
    assertEquals(100, upperPlayers.get(3).currentSquareId());
    assertFalse(upperPlayers.get(0).isTurn());
    assertFalse(upperPlayers.get(1).isTurn());
    assertFalse(upperPlayers.get(2).isTurn());
    assertTrue(upperPlayers.get(3).isTurn());

    MvcResult lowerResult = mockMvc.perform(get("/game")
            .param("turnPlayer", "0"))
        .andExpect(status().isOk())
        .andExpect(model().attributeExists("currentTurnPlayer"))
        .andReturn();

    BoardController.Player lowerTurn =
        (BoardController.Player) lowerResult.getModelAndView().getModel().get("currentTurnPlayer");
    assertNotNull(lowerTurn);
    assertEquals(1, lowerTurn.id());
    assertTrue(lowerTurn.isTurn());

    @SuppressWarnings("unchecked")
    List<BoardController.Player> lowerPlayers =
        (List<BoardController.Player>) lowerResult.getModelAndView().getModel().get("players");
    assertTrue(lowerPlayers.get(0).isTurn());
    assertFalse(lowerPlayers.get(1).isTurn());
  }

  private static int countMatches(Pattern pattern, String text) {
    Matcher matcher = pattern.matcher(text);
    int count = 0;
    while (matcher.find()) {
      count++;
    }
    return count;
  }
}
