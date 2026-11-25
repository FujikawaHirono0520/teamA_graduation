package com.example.teama_management.controller;

import com.example.teama_management.repository.AssignmentManageRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
