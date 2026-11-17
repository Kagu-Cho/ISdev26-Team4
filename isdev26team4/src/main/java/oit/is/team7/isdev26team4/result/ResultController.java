package oit.is.team7.isdev26team4.result;

import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.SessionAttribute;
import oit.is.team7.isdev26team4.result.GraduationService.EnrollmentStatus;
import oit.is.team7.isdev26team4.result.GraduationService.StudentStatus;

/**
 * 卒業判定済みの一覧を表示する。順位の採番や勝敗の判定、DB保存は行わない。
 * 共有API未確定のため、後続の試合処理がセッションのresultSnapshotに
 * 現在の試合のスナップショットを設定する暫定の受け渡し境界とする。
 * 新規試合への切り替え時には呼び出し側でこの属性を更新・削除する必要がある。
 */
@Controller
public class ResultController {

  public static final String RESULT_SESSION_ATTRIBUTE = "resultSnapshot";

  public record ParticipantResult(String name, boolean cpu, StudentStatus status) {
    public ParticipantResult {
      if (name == null || name.isBlank() || status == null) {
        throw new IllegalArgumentException("参加者名と学生状態は必須です");
      }
    }
  }

  /** 人間とCPUを含めた全参加者を渡す。入力リストの後からの変更は反映しない。 */
  public record ResultSnapshot(List<ParticipantResult> participants) {
    public ResultSnapshot {
      if (participants == null || participants.stream().anyMatch(p -> p == null)) {
        throw new IllegalArgumentException("参加者一覧が不正です");
      }
      participants = List.copyOf(participants);
    }
  }

  @GetMapping("/result")
  public String showResult(
      @SessionAttribute(name = RESULT_SESSION_ATTRIBUTE, required = false) ResultSnapshot snapshot,
      Model model) {
    boolean completed = snapshot != null && !snapshot.participants().isEmpty()
        && snapshot.participants().stream()
            .allMatch(p -> p.status().enrollment() == EnrollmentStatus.GRADUATED);
    model.addAttribute("completed", completed);
    // 同順位はそのまま表示し、入力順を維持する。独自の優先順位は付けない。
    List<ParticipantResult> results = completed
        ? snapshot.participants().stream()
            .sorted(Comparator.comparingInt(p -> p.status().graduationRank())).toList()
        : List.of();
    model.addAttribute("results", results);
    return "result";
  }
}
