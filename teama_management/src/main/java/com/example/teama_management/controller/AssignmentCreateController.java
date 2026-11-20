package com.example.teama_management.controller;

import com.example.teama_management.form.AssignmentCreateForm;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.List;

@Controller
public class AssignmentCreateController {

    private final AssignmentService assignmentService;

    public AssignmentCreateController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    // 課題登録画面を表示する
    @GetMapping("/assignments/create")
    public String showCreateForm(HttpSession session, Model model) {
        if (session.getAttribute("loginUid") == null) {
            return "redirect:/login";
        }

        String loginUid = session.getAttribute("loginUid").toString();
        model.addAttribute("name", session.getAttribute("loginName"));
        model.addAttribute("form", new AssignmentCreateForm());
        model.addAttribute("leaderTeams", assignmentService.findLeaderTeams(loginUid));
        return "assignment_create";
    }

    // 課題の登録処理を行う
    @PostMapping("/assignments/create")
    public String createAssignment(@ModelAttribute("form") AssignmentCreateForm form,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {
        if (session.getAttribute("loginUid") == null) {
            return "redirect:/login";
        }

        String loginUid = session.getAttribute("loginUid").toString();
        boolean hasError = false;

        // 7. 入力内容を確認する（3a-1. 必須項目が未入力の場合）
        if (isBlank(form.getTitle())) {
            model.addAttribute("titleErrorMessage", "課題名を入力してください");
            hasError = true;
        }

        boolean isTeamTarget = "team".equals(form.getTargetType());
        boolean isIndividualTarget = "individual".equals(form.getTargetType());
        List<String> assignees = form.getAssigneeIds();

        if ((isTeamTarget && isBlank(form.getTeamId()))
                || (isIndividualTarget && (assignees == null || assignees.isEmpty()))
                || (!isTeamTarget && !isIndividualTarget)) {
            model.addAttribute("targetErrorMessage", "対象を選択してください");
            hasError = true;
        }

        // 4a-1. 締め切り日時が過去の場合
        if (form.getDueDate() != null && form.getDueDate().isBefore(LocalDateTime.now())) {
            model.addAttribute("dueDateErrorMessage", "締め切り日時を再設定してください");
            hasError = true;
        }

        if (hasError) {
            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("leaderTeams", assignmentService.findLeaderTeams(loginUid));
            return "assignment_create";
        }

        try {
            // 10〜14. ステータス「未着手」設定・添付資料保存・Firestore登録
            assignmentService.createAssignment(form, loginUid);

            // 16. 課題一覧画面に遷移し、スナックバー「(課題名)を登録しました」を表示する
            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", form.getTitle().trim() + "を登録しました");
            return "redirect:/assignments";
        } catch (Exception e) {
            // 5a-1. 課題の登録に失敗した場合
            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("leaderTeams", assignmentService.findLeaderTeams(loginUid));
            model.addAttribute("errorMessage", "課題の登録に失敗しました");
            return "assignment_create";
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}