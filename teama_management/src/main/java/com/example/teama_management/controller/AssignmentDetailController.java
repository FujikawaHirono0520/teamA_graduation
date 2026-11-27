package com.example.teama_management.controller;

import com.example.teama_management.repository.AssignmentDetailRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import java.util.Map;

@Controller
public class AssignmentDetailController {

    private final AssignmentDetailRepository assignmentDetailRepository;

    public AssignmentDetailController(
            AssignmentDetailRepository assignmentDetailRepository) {
        this.assignmentDetailRepository = assignmentDetailRepository;
    }

    // ユーザー側の課題詳細画面を表示する
    @GetMapping("/assignments/{id}")
    public String showDetail(
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
                    assignmentDetailRepository.findDetailData(
                            assignmentId, uid);

            if (assignment == null) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage", "課題が見つからないか、表示する権限がありません");
                return "redirect:/assignments";
            }

            // コメント一覧を取得する
            model.addAttribute("comments",
                    assignmentDetailRepository.findComments(
                            assignmentId, uid));

            // チェックリスト一覧を取得する
            model.addAttribute("checklists",
                    assignmentDetailRepository.findChecklist(
                            assignmentId, uid));

            model.addAttribute("name", session.getAttribute("loginName"));
            model.addAttribute("assignment", assignment);

            return "assignment_detail";

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "課題詳細画面の表示に失敗しました");

            return "redirect:/assignments";
        }
    }

    // ステータスを変更する
    @PostMapping("/assignments/{id}/status")
    public String updateStatus(
            @PathVariable("id") String assignmentId,
            @RequestParam("status") String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.updateStatus(
                    assignmentId, uid, status);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "ステータスを変更しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "ステータスの変更に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }

    // 課題を提出する
    @PostMapping("/assignments/{id}/submit")
    public String submitAssignment(
            @PathVariable("id") String assignmentId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.submitAssignment(
                    assignmentId, uid);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "課題を提出しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "課題の提出に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }

    // コメントを投稿する
    @PostMapping("/assignments/{id}/comment")
    public String addComment(
            @PathVariable("id") String assignmentId,
            @RequestParam("body") String body,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.addComment(
                    assignmentId, uid, body);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "コメントを投稿しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "コメントの投稿に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }

    // チェックリストを追加する
    @PostMapping("/assignments/{id}/checklist")
    public String addChecklist(
            @PathVariable("id") String assignmentId,
            @RequestParam("title") String title,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.addChecklist(
                    assignmentId, uid, title);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "チェックリストを追加しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "チェックリストの追加に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }

    // チェックリストの状態を変更する
    @PostMapping("/assignments/{id}/checklist/{checklistId}/status")
    public String updateChecklistStatus(
            @PathVariable("id") String assignmentId,
            @PathVariable("checklistId") String checklistId,
            @RequestParam(value = "isChecked", defaultValue = "false") boolean isChecked,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.updateChecklistStatus(
                    assignmentId, checklistId, uid, isChecked);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "チェック状態を変更しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "チェック状態の変更に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }

    // チェックリストを削除する
    @PostMapping("/assignments/{id}/checklist/{checklistId}/delete")
    public String deleteChecklist(
            @PathVariable("id") String assignmentId,
            @PathVariable("checklistId") String checklistId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        try {
            assignmentDetailRepository.deleteChecklist(
                    assignmentId, checklistId, uid);

            redirectAttributes.addFlashAttribute(
                    "snackbarMessage", "チェックリストを削除しました");

        } catch (Exception e) {
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage", "チェックリストの削除に失敗しました");
        }

        return "redirect:/assignments/" + assignmentId;
    }
}
