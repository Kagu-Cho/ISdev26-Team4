package oit.is.team7.isdev26team4.result;

import jakarta.servlet.http.HttpServletResponse;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;
import oit.is.team7.isdev26team4.result.GraduationService.StudentStatus;

/** 保存済み結果を読み取って表示する。順位採番、卒業判定、状態更新は行わない。 */
@Controller
public class ResultController {
  private final ObjectProvider<GameResultSource> sources;

  public ResultController(ObjectProvider<GameResultSource> sources) {
    this.sources = sources;
  }

  /**
   * #11/#12の接続口。最新の保存状態を読み取る実装をSpring Beanとして登録する。
   * allowedUsernamesには保存された本人識別に基づく実際の参加者と開始権限者だけを設定する。
   * HTTP入力や古いセッションから状態を組み立てないこと。
   * DB接続が未実装の場合、結果取得は503を返す。ダミーの本番データは作らない。
   */
  @FunctionalInterface
  public interface GameResultSource {
    Optional<GameResultState> find(UUID gameId);
  }

  public enum GameStatus { WAITING, PLAYING, FINISHED }

  public record ParticipantResult(UUID participantId, int playerNumber, String displayName,
      boolean cpu, StudentStatus status) {
    public ParticipantResult {
      if (participantId == null || playerNumber < 1 || playerNumber > 4
          || displayName == null || displayName.isBlank() || status == null) {
        throw new IllegalArgumentException("参加者情報が不正です");
      }
    }
  }

  /** 読み取り時点のスナップショット。権限情報は結果JSONには含めない。 */
  public record GameResultState(UUID gameId, GameStatus status, Set<String> allowedUsernames,
      List<ParticipantResult> players) {
    public GameResultState {
      if (gameId == null || status == null || allowedUsernames == null || players == null
          || allowedUsernames.stream().anyMatch(name -> name == null || name.isBlank())
          || players.stream().anyMatch(player -> player == null) || players.size() > 4) {
        throw new IllegalArgumentException("試合情報が不正です");
      }
      if (players.stream().map(ParticipantResult::participantId).distinct().count() != players.size()
          || players.stream().map(ParticipantResult::playerNumber).distinct().count() != players.size()) {
        throw new IllegalArgumentException("参加者IDと表示番号は試合内で一意です");
      }
      allowedUsernames = Set.copyOf(allowedUsernames);
      players = List.copyOf(players);
    }
  }

  public record ResultRow(UUID participantId, int playerNumber, String displayName, boolean cpu,
      int credits, int repeatYears, EnrollmentStatus enrollmentStatus, int graduationRank) {}

  public record ResultResponse(UUID gameId, GameStatus status, List<ResultRow> players) {
    public ResultResponse { players = List.copyOf(players); }
  }

  @GetMapping(value = "/api/games/{gameId}/result", produces = MediaType.APPLICATION_JSON_VALUE)
  @ResponseBody
  public ResultResponse result(@PathVariable UUID gameId, Principal principal) {
    GameResultState state = authorizedState(gameId, principal);
    if (!completed(state)) {
      throw new ResponseStatusException(HttpStatus.CONFLICT, "試合結果は未確定です");
    }
    return new ResultResponse(state.gameId(), state.status(), rows(state));
  }

  @GetMapping("/result")
  public String showResult(@RequestParam(required = false) UUID gameId, Principal principal,
      Model model, HttpServletResponse response) {
    if (gameId == null) {
      model.addAttribute("completed", false);
      model.addAttribute("selectGame", true);
      model.addAttribute("results", List.of());
      return "result";
    }
    return showGameResult(gameId, principal, model, response);
  }

  @GetMapping("/games/{gameId}/result")
  public String showGameResult(@PathVariable UUID gameId, Principal principal, Model model,
      HttpServletResponse response) {
    GameResultState state = authorizedState(gameId, principal);
    boolean completed = completed(state);
    model.addAttribute("gameId", gameId);
    model.addAttribute("selectGame", false);
    model.addAttribute("completed", completed);
    model.addAttribute("results", completed ? rows(state) : List.of());
    if (!completed) { response.setStatus(HttpStatus.CONFLICT.value()); }
    return "result";
  }

  private GameResultState authorizedState(UUID gameId, Principal principal) {
    if (principal == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
    }
    GameResultSource source = sources.getIfAvailable();
    if (source == null) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "試合状態の取得処理が未接続です");
    }
    GameResultState state = source.find(gameId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    if (!gameId.equals(state.gameId())) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "取得した試合IDが一致しません");
    }
    if (!state.allowedUsernames().contains(principal.getName())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    return state;
  }

  private boolean completed(GameResultState state) {
    return state.status() == GameStatus.FINISHED && !state.players().isEmpty()
        && state.players().stream().allMatch(p -> p.status().enrollment() == EnrollmentStatus.GRADUATED);
  }

  private List<ResultRow> rows(GameResultState state) {
    // 保存済み順位を表示順に使うだけで、同順位の解消や順位の再計算はしない。
    return state.players().stream()
        .sorted(Comparator.comparingInt(p -> p.status().graduationRank()))
        .map(p -> new ResultRow(p.participantId(), p.playerNumber(), p.displayName(), p.cpu(),
            p.status().credits(), p.status().repeatYears(), p.status().enrollment(),
            p.status().graduationRank())).toList();
  }
}
