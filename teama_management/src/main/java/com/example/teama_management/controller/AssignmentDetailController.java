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
}
