
package com.example.teama_management.controller;

import com.example.teama_management.form.AssignmentCreateForm;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Controller
public class AssignmentCreateController {

    private final AssignmentService assignmentService;

    public AssignmentCreateController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // 課題登録画面を表示する
    @GetMapping("/assignments/create")
    public String showCreateForm(HttpSession session, Model model)
            throws Exception {

        if (session.getAttribute("loginUid") == null) {
            return "redirect:/login";
        }

        String loginUid = session.getAttribute("loginUid").toString();

        model.addAttribute("name", session.getAttribute("loginName"));
        model.addAttribute("form", new AssignmentCreateForm());
        model.addAttribute("leaderTeams",
                assignmentService.findLeaderTeams(loginUid));

        return "assignment_create";
    }

    // 課題登録画面専用のユーザー検索
    @GetMapping("/assignments/create/search")
    @ResponseBody
    public List<Map<String, String>> searchUsers(
            @RequestParam("keyword") String keyword,
            HttpSession session) throws Exception {

        String loginUid = (String) session.getAttribute("loginUid");

        if (loginUid == null) {
            return List.of();
        }

        return assignmentService.searchLeaderTeamMembers(loginUid, keyword);
    }

    // 課題の登録処理を行う
    @PostMapping("/assignments/create")
    public String createAssignment(
            @ModelAttribute("form") AssignmentCreateForm form,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) throws Exception {

        if (session.getAttribute("loginUid") == null) {
            return "redirect:/login";
        }

        String loginUid = session.getAttribute("loginUid").toString();
        boolean hasError = false;

        // 課題名の入力を確認する
        if (isBlank(form.getTitle())) {
            model.addAttribute("titleErrorMessage", "課題名を入力してください");
            hasError = true;
        }

        boolean isTeamTarget = "team".equals(form.getTargetType());
        boolean isIndividualTarget = "individual".equals(form.getTargetType());

        List<String> assignees = form.getAssigneeIds();

        // 修正　2026年11月25日
        if ((isTeamTarget && isBlank(form.getTeamId()))
            || (isIndividualTarget && (assignees == null || assignees.isEmpty()))
            || (!isTeamTarget && !isIndividualTarget)) {

            model.addAttribute("targetErrorMessage", "対象を選択してください");
            hasError = true;
        }

        // 締め切り日時が過去の場合
        if (form.getDueDate() != null
                && form.getDueDate().isBefore(LocalDateTime.now())) {

            model.addAttribute(
                    "dueDateErrorMessage",
                    "締め切り日時を再設定してください");

            hasError = true;
        }

        if (hasError) {
            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("leaderTeams",
                    assignmentService.findLeaderTeams(loginUid));

            return "assignment_create";
        }

        try {

            // 課題を登録する
            assignmentService.createAssignment(form, loginUid);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage",
                    form.getTitle().trim() + "を登録しました");

            return "redirect:/assignments";

        } catch (IllegalArgumentException e) {

            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("leaderTeams",
                    assignmentService.findLeaderTeams(loginUid));
            model.addAttribute("targetErrorMessage", e.getMessage());

            return "assignment_create";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("leaderTeams",
                    assignmentService.findLeaderTeams(loginUid));
            model.addAttribute("errorMessage", "課題の登録に失敗しました");

            return "assignment_create";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
