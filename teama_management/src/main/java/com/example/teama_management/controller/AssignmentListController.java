package com.example.teama_management.controller;

import com.example.teama_management.model.AssignmentItem;
import com.example.teama_management.model.TeamMembership;
import com.example.teama_management.repository.AssignmentRepository;
import com.example.teama_management.repository.TeamRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Objects;

@Controller
public class AssignmentListController {

    private static final List<String[]> USER_FILTERS = List.of(
            new String[]{"all", "すべて"}, new String[]{"notStarted", "未着手"},
            new String[]{"inProgress", "着手中"}, new String[]{"waitingApproval", "承認待ち"},
            new String[]{"resubmit", "再提出"}, new String[]{"approved", "完了"});
    private static final List<String[]> LEADER_FILTERS = List.of(
            new String[]{"all", "すべて"}, new String[]{"waitingApproval", "承認待ち"},
            new String[]{"approved", "承認済み"});

    private final TeamRepository teamRepository;
    private final AssignmentRepository assignmentRepository;

    public AssignmentListController(TeamRepository teamRepository,
                                    AssignmentRepository assignmentRepository) {
        this.teamRepository = teamRepository;
        this.assignmentRepository = assignmentRepository;
    }

    @GetMapping("/assignments")
    public String list(@RequestParam(required = false) String teamId,
                       @RequestParam(name = "_SearchTaskName", required = false, defaultValue = "") String keyword,
                       @RequestParam(required = false, defaultValue = "all") String status,
                       HttpSession session, Model model) {
        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合はログイン画面に戻る
        if (uid == null) {
            return "redirect:/login";
        }

        // 所属チームを取得する
        List<TeamMembership> memberships;
        try {
            memberships = teamRepository.findMemberships(uid);
        } catch (Exception e) {
            model.addAttribute("memberships", List.of());
            model.addAttribute("teamError", "チームの取得に失敗しました");
            return "assignment-list";
        }
        model.addAttribute("memberships", memberships);

        // どのチームにも参加していない場合
        if (memberships.isEmpty()) {
            session.removeAttribute("selectedTeamId");
            return "assignment-list";
        }

        // 選択中のチームを決める（指定があればそれ、なければ前回の選択、なければ先頭）
        String selected = teamId != null ? teamId : (String) session.getAttribute("selectedTeamId");
        TeamMembership current = memberships.stream()
                .filter(m -> Objects.equals(m.teamId(), selected))
                .findFirst().orElse(memberships.get(0));
        session.setAttribute("selectedTeamId", current.teamId());
        boolean leader = current.isLeader();

        List<String[]> filters = leader ? LEADER_FILTERS : USER_FILTERS;
        String activeStatus = filters.stream().anyMatch(f -> f[0].equals(status)) ? status : "all";

        model.addAttribute("current", current);
        model.addAttribute("leader", leader);
        model.addAttribute("filters", filters);
        model.addAttribute("keyword", keyword);
        model.addAttribute("status", activeStatus);

        // 課題を取得する
        try {
            List<AssignmentItem> items =
                    assignmentRepository.find(current.teamId(), uid, leader, keyword, activeStatus);
            model.addAttribute("assignments", items);
            if (items.isEmpty()) {
                boolean searching = !keyword.isBlank() || !"all".equals(activeStatus);
                String message = searching ? "条件に合致する課題がありません"
                        : (leader ? "登録している課題はありません" : "担当している課題はありません");
                model.addAttribute("emptyMessage", message);
            }
        } catch (Exception e) {
            model.addAttribute("assignments", List.of());
            model.addAttribute("listError", "課題の取得に失敗しました");
        }
        return "assignment-list";
    }
}