package com.example.teama_management.controller;

import com.example.teama_management.form.AssignmentCreateForm;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;

import org.springframework.ui.Model;

import org.springframework.web.bind.annotation.GetMapping;

import org.springframework.web.bind.annotation.ModelAttribute;

import org.springframework.web.bind.annotation.PathVariable;

import org.springframework.web.bind.annotation.PostMapping;

import org.springframework.web.bind.annotation.RequestParam;

import org.springframework.web.bind.annotation.ResponseBody;

import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDateTime;

import java.util.List;

import java.util.Map;

@Controller

public class AssignmentEditController {

    private final AssignmentService assignmentService;

    public AssignmentEditController(AssignmentService assignmentService) {

        this.assignmentService = assignmentService;

    }

    // 課題編集画面を表示する

    @GetMapping("/assignments/{id}/edit")

    public String showEditForm(

            @PathVariable("id") String id,

            HttpSession session,

            Model model) throws Exception {

        if (session.getAttribute("loginUid") == null) {

            return "redirect:/login";

        }

        String loginUid = session.getAttribute("loginUid").toString();

        // 既存課題データをFirestoreから取得する

        AssignmentCreateForm form =

                assignmentService.findAssignmentForEdit(id, loginUid);

        if (form == null) {

            return "redirect:/assignments";

        }

        model.addAttribute("assignmentId", id);

        model.addAttribute("name", session.getAttribute("loginName"));

        model.addAttribute("form", form);

        // 選択済み担当者の情報を取得する

        model.addAttribute("selectedUsers",

                assignmentService.findAssigneeUsers(form.getAssigneeIds()));

        model.addAttribute("leaderTeams",

                assignmentService.findLeaderTeams(loginUid));

        return "assignment_edit";

    }

    // 課題編集画面で担当者を検索する

    @GetMapping("/assignments/{id}/edit/search")

    @ResponseBody

    public List<Map<String, String>> searchUsers(

            @PathVariable("id") String id,

            @RequestParam("keyword") String keyword,

            HttpSession session) throws Exception {

        String loginUid = (String) session.getAttribute("loginUid");

        if (loginUid == null) {

            return List.of();

        }

        return assignmentService.searchLeaderTeamMembers(loginUid, keyword);

    }

    // 課題情報を更新する

    @PostMapping("/assignments/{id}/edit")

    public String updateAssignment(

            @PathVariable("id") String id,

            @ModelAttribute("form") AssignmentCreateForm form,

            HttpSession session,

            Model model,

            RedirectAttributes redirectAttributes) throws Exception {

        if (session.getAttribute("loginUid") == null) {

            return "redirect:/login";

        }

        String loginUid = session.getAttribute("loginUid").toString();

        boolean hasError = false;

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

        if (form.getDueDate() != null
                && form.getDueDate().isBefore(LocalDateTime.now())) {

            model.addAttribute("dueDateErrorMessage", "締め切り日時を再設定してください");

            hasError = true;

        }

        if (hasError) {

            model.addAttribute("assignmentId", id);

            model.addAttribute("name", session.getAttribute("loginName"));

            model.addAttribute("leaderTeams",

                    assignmentService.findLeaderTeams(loginUid));

            // 選択済み担当者の情報を再取得する

            model.addAttribute("selectedUsers",

                    assignmentService.findAssigneeUsers(form.getAssigneeIds()));

            return "assignment_edit";

        }

        try {

            // 課題情報を更新する

            assignmentService.updateAssignment(id, form, loginUid);

            redirectAttributes.addFlashAttribute(

                    "snackbarMessage",

                    "課題情報を更新しました");

            return "redirect:/assignments";

        } catch (IllegalArgumentException e) {

            model.addAttribute("assignmentId", id);

            model.addAttribute("name", session.getAttribute("loginName"));

            model.addAttribute("leaderTeams",

                    assignmentService.findLeaderTeams(loginUid));

            model.addAttribute("targetErrorMessage", e.getMessage());

            // 選択済み担当者の情報を再取得する

            model.addAttribute("selectedUsers",

                    assignmentService.findAssigneeUsers(form.getAssigneeIds()));

            return "assignment_edit";

        } catch (Exception e) {

            e.printStackTrace();

            model.addAttribute("assignmentId", id);

            model.addAttribute("name", session.getAttribute("loginName"));

            model.addAttribute("leaderTeams",

                    assignmentService.findLeaderTeams(loginUid));

            model.addAttribute("errorMessage", "課題の更新に失敗しました");

            // 選択済み担当者の情報を再取得する

            model.addAttribute("selectedUsers",

                    assignmentService.findAssigneeUsers(form.getAssigneeIds()));

            return "assignment_edit";

        }

    }

    // 空文字か確認する

    private boolean isBlank(String s) {

        return s == null || s.isBlank();

    }

}
