package oit.is.team7.isdev26team4.game;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;
import org.springframework.web.servlet.view.AbstractView;

class BoardControllerTest {

  private MockMvc mockMvc;
  private BoardController controller;

  @BeforeEach
  void setUp() {
    controller = new BoardController();
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
        .setViewResolvers((viewName, locale) -> new AbstractView() {
          @Override
          protected void renderMergedOutputModel(
              Map<String, Object> model,
              HttpServletRequest request,
              HttpServletResponse response) {
            // MockMvc用のダミー描画
          }
        })
        .build();
  }

  @Test
  @DisplayName("AC1: 盤面を開いたとき100マス以上のマップと4人の駒、および接続線データが表示される")
  void testBoardDisplaysAtLeast100SquaresAnd4Players() throws Exception {
    // 1. Controller 単体検証
    Model model = new ConcurrentModel();
    String viewName = controller.game(1, 1, 1, 1, 1, model);
    assertEquals("game", viewName);

    @SuppressWarnings("unchecked")
    List<BoardController.Square> squares = (List<BoardController.Square>) model.getAttribute("squares");
    assertNotNull(squares);
    assertEquals(100, squares.size());

    // 各マスに x, y 座標が設定されていることを検証
    BoardController.Square firstSquare = squares.get(0);
    assertTrue(firstSquare.x() > 0);
    assertTrue(firstSquare.y() > 0);

    // マス間を接続するポリライン座標データが存在することを検証
    assertTrue(model.containsAttribute("trackPoints"));
    String trackPoints = (String) model.getAttribute("trackPoints");
    assertNotNull(trackPoints);
    assertTrue(trackPoints.contains(","));

    @SuppressWarnings("unchecked")
    List<BoardController.Player> players = (List<BoardController.Player>) model.getAttribute("players");
    assertNotNull(players);
    assertEquals(4, players.size());

    // 2. MockMvc によるリクエスト / モデル検証
    mockMvc.perform(get("/game"))
        .andExpect(status().isOk())
        .andExpect(view().name("game"))
        .andExpect(model().attributeExists("squares"))
        .andExpect(model().attribute("squares", hasSize(100)))
        .andExpect(model().attributeExists("trackPoints"))
        .andExpect(model().attributeExists("players"))
        .andExpect(model().attribute("players", hasSize(4)));
  }

  @Test
  @DisplayName("AC2: 異なる位置と手番を与えたとき駒の位置と手番表示が反映される")
  void testBoardReflectsDifferentPositionsAndTurn() throws Exception {
    // 1. Controller 単体検証
    Model model = new ConcurrentModel();
    String viewName = controller.game(15, 30, 55, 80, 3, model);
    assertEquals("game", viewName);

    @SuppressWarnings("unchecked")
    List<BoardController.Player> players = (List<BoardController.Player>) model.getAttribute("players");
    assertNotNull(players);
    assertEquals(15, players.get(0).currentSquareId());
    assertEquals(30, players.get(1).currentSquareId());
    assertEquals(55, players.get(2).currentSquareId());
    assertEquals(80, players.get(3).currentSquareId());

    BoardController.Player currentTurn = (BoardController.Player) model.getAttribute("currentTurnPlayer");
    assertNotNull(currentTurn);
    assertEquals(3, currentTurn.id());
    assertTrue(currentTurn.isTurn());

    // 2. MockMvc によるパラメータ付きリクエスト検証
    mockMvc.perform(get("/game")
            .param("p1Pos", "15")
            .param("p2Pos", "30")
            .param("p3Pos", "55")
            .param("p4Pos", "80")
            .param("turnPlayer", "3"))
        .andExpect(status().isOk())
        .andExpect(view().name("game"))
        .andExpect(model().attributeExists("currentTurnPlayer"));
  }
}
