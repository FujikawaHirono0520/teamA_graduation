
package com.example.teama_management.controller;

import com.example.teama_management.repository.AssignmentManageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;

@Controller
public class AssignmentManageController {

    private final AssignmentManageRepository assignmentManageRepository;

    public AssignmentManageController(
            AssignmentManageRepository assignmentManageRepository) {
        this.assignmentManageRepository = assignmentManageRepository;
    }

    // 課題管理画面を表示する
    @GetMapping("/assignments/{id}/manage")
    public String showManage(
            @PathVariable("id") String assignmentId,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            Map<String, Object> assignment =
                    assignmentManageRepository.findManageData(assignmentId, uid);

            if (assignment == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "課題が見つからないか、表示する権限がありません");
                return "redirect:/assignments";
            }

            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("assignment", assignment);
            return "assignment_manage";

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "課題管理画面の表示に失敗しました");
            return "redirect:/assignments";
        }
    }

    // ユーザーの提出を承認する
    @PostMapping("/assignments/{id}/users/{userId}/approve")
    public String approveSubmission(
            @PathVariable("id") String assignmentId,
            @PathVariable("userId") String userId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            boolean approved = assignmentManageRepository.approveSubmission(
                    assignmentId, userId, uid);

            if (approved) {
                redirectAttributes.addFlashAttribute(
                        "snackbarMessage", "提出を承認しました");
            } else {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "承認できませんでした。提出状況を確認してください");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "提出の承認に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId + "/manage";
    }

    // チーム全体の提出を承認する
    @PostMapping("/assignments/{id}/users/team/approve")
    public String approveTeamSubmission(
            @PathVariable("id") String assignmentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            boolean approved = assignmentManageRepository.approveSubmission(
                    assignmentId, "team", uid);

            if (approved) {
                redirectAttributes.addFlashAttribute(
                        "snackbarMessage", "提出を承認しました");
            } else {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "承認できませんでした。提出状況を確認してください");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "提出の承認に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId + "/manage";
    }

    // ユーザーに再提出を依頼する
    @PostMapping("/assignments/{id}/users/{userId}/resubmit")
    public String requestResubmission(
            @PathVariable("id") String assignmentId,
            @PathVariable("userId") String userId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        if (reason == null || reason.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "再提出理由を入力してください");
            return "redirect:/assignments/" + assignmentId + "/manage";
        }

        try {
            boolean requested = assignmentManageRepository.requestResubmission(
                    assignmentId, userId, uid, reason.trim());

            if (requested) {
                redirectAttributes.addFlashAttribute(
                        "snackbarMessage", "再提出を依頼しました");
            } else {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "再提出を依頼できませんでした。提出状況を確認してください");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "再提出の依頼に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId + "/manage";
    }

    // チーム全体に再提出を依頼する
    @PostMapping("/assignments/{id}/users/team/resubmit")
    public String requestTeamResubmission(
            @PathVariable("id") String assignmentId,
            @RequestParam("reason") String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        if (reason == null || reason.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "再提出理由を入力してください");
            return "redirect:/assignments/" + assignmentId + "/manage";
        }

        try {
            boolean requested = assignmentManageRepository.requestResubmission(
                    assignmentId, "team", uid, reason.trim());

            if (requested) {
                redirectAttributes.addFlashAttribute(
                        "snackbarMessage", "再提出を依頼しました");
            } else {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "再提出を依頼できませんでした。提出状況を確認してください");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "再提出の依頼に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId + "/manage";
    }

    // 課題を削除する
    @PostMapping("/assignments/{id}/delete")
    public String deleteAssignment(
            @PathVariable("id") String assignmentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            boolean deleted = assignmentManageRepository.deleteAssignment(
                    assignmentId, uid);

            if (deleted) {
                redirectAttributes.addFlashAttribute(
                        "snackbarMessage", "課題を削除しました");
            } else {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "課題を削除できませんでした");
            }

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage", "課題の削除に失敗しました");
        }

        return "redirect:/assignments";
    }
}
