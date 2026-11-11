package oit.is.team7.isdev26team4.game;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class BoardController {

  /**
   * マス情報データ構造
   * x, y 座標を明示的に保持し、データ構造の変更に画面・接続線が完全に連動する設計
   */
  public record Square(int id, int x, int y, String name, String type) {}

  public record Player(int id, String name, int currentSquareId, String color, boolean isTurn) {}

  /**
   * 100マスのすごろく盤面データ（人生ゲーム風のコース座標）
   * 下段（1年生・入学）から上段（4年生・卒業）に向かって登るS字蛇行コース（全100マス）
   */
  public static List<Square> createDefaultSquares() {
    List<Square> squares = new ArrayList<>();

    for (int i = 1; i <= 100; i++) {
      int row = (i - 1) / 10; // 0〜9段
      int col = (i - 1) % 10; // 0〜9列

      // 偶数段は左から右、奇数段は右から左への蛇行
      int x = (row % 2 == 0) ? (75 + col * 115) : (75 + (9 - col) * 115);
      // 下段(780px)から上段(78px)へ進むレイアウト
      int y = 780 - (row * 78);

      String type;
      String name;
      if (i == 1) {
        type = "START";
        name = "入学";
      } else if (i == 100) {
        type = "GOAL";
        name = "卒業";
      } else if (i % 25 == 0) {
        type = "EXAM";
        name = "進級試験";
      } else if (i % 10 == 0) {
        type = "EVENT";
        name = "大学祭";
      } else if (i % 3 == 0) {
        type = "STUDY";
        name = "講義";
      } else if (i % 2 == 0) {
        type = "CLUB";
        name = "サークル";
      } else {
        type = "WORK";
        name = "バイト";
      }

      squares.add(new Square(i, x, y, name, type));
    }
    return squares;
  }

  @GetMapping("/game")
  public String game(
      @RequestParam(name = "p1Pos", defaultValue = "1") int p1Pos,
      @RequestParam(name = "p2Pos", defaultValue = "1") int p2Pos,
      @RequestParam(name = "p3Pos", defaultValue = "1") int p3Pos,
      @RequestParam(name = "p4Pos", defaultValue = "1") int p4Pos,
      @RequestParam(name = "turnPlayer", defaultValue = "1") int turnPlayer,
      Model model) {

    List<Square> squares = createDefaultSquares();

    List<Player> players = List.of(
        new Player(1, "プレイヤー1", p1Pos, "#e53e3e", turnPlayer == 1),
        new Player(2, "プレイヤー2", p2Pos, "#3182ce", turnPlayer == 2),
        new Player(3, "プレイヤー3", p3Pos, "#38a169", turnPlayer == 3),
        new Player(4, "プレイヤー4", p4Pos, "#d69e2e", turnPlayer == 4)
    );

    Player currentTurn = players.stream()
        .filter(Player::isTurn)
        .findFirst()
        .orElse(players.get(0));

    // マス間を線で結ぶためのSVG用ポリライン座標文字列 ("x1,y1 x2,y2 ...")
    String trackPoints = squares.stream()
        .map(s -> s.x() + "," + s.y())
        .collect(Collectors.joining(" "));

    model.addAttribute("squares", squares);
    model.addAttribute("trackPoints", trackPoints);
    model.addAttribute("players", players);
    model.addAttribute("currentTurnPlayer", currentTurn);

    return "game";
  }
}
