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
      boolean cpu, StudentStatus status, boolean worst) {
    /** 卒業済み参加者など、ワーストでない保存状態の受け渡し。 */
    public ParticipantResult(UUID participantId, int playerNumber, String displayName,
        boolean cpu, StudentStatus status) {
      this(participantId, playerNumber, displayName, cpu, status, false);
    }
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

  /** graduationRankは保存値。未卒業ワーストのresultRankだけ表示用に4とする。 */
  public record ResultRow(UUID participantId, int playerNumber, String displayName, boolean cpu,
      int credits, int repeatYears, EnrollmentStatus enrollmentStatus, int graduationRank,
      int resultRank, boolean worst) {}

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
    if (state.status() != GameStatus.FINISHED || state.players().size() != 4) {
      return false;
    }
    // FINISHED、worstはいずれも保存済み値を参照し、GETでは確定処理をしない。
    if (state.players().stream().anyMatch(p -> p.worst()
        != (p.status().enrollment() != EnrollmentStatus.GRADUATED))) { return false; }
    List<Integer> ranks = state.players().stream()
        .filter(p -> p.status().enrollment() == EnrollmentStatus.GRADUATED)
        .map(p -> p.status().graduationRank()).sorted().toList();
    // 保存済み順位に重複・欠番がある場合は結果未確定。GETでは補正しない。
    for (int index = 0; index < ranks.size(); index++) {
      if (ranks.get(index) != index + 1) { return false; }
    }
    // 4人中3人卒業で残り1人を未卒業ワーストとして確定できる。
    // FINISHEDへの状態更新・卒業順位の採番は#12から呼ぶ判定/保存処理の責任。
    return ranks.size() >= 3;
  }

  private List<ResultRow> rows(GameResultState state) {
    // 卒業順位を再計算せず、未卒業ワーストを最後に表示する。
    return state.players().stream()
        .map(p -> new ResultRow(p.participantId(), p.playerNumber(), p.displayName(), p.cpu(),
            p.status().credits(), p.status().repeatYears(), p.status().enrollment(),
            p.status().graduationRank(),
            p.worst() ? 4 : p.status().graduationRank(), p.worst()))
        .sorted(Comparator.comparingInt(ResultRow::resultRank)).toList();
  }
}
