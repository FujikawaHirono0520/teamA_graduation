package com.example.teama_management.controller;

import com.example.teama_management.form.PasswordChangeForm;
import com.example.teama_management.service.FirebaseAuthRestClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordChangeController {

    private final FirebaseAuthRestClient authClient;

    public PasswordChangeController(FirebaseAuthRestClient authClient) {
        this.authClient = authClient;
    }

    // パスワード変更画面を表示する
    @GetMapping("/password/change")
    public String showPasswordChange(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "password_change";
    }

    // キャンセル・戻る押下時は変更せず設定画面へ戻る
    @GetMapping("/password/change/cancel")
    public String cancelPasswordChange() {
        return "redirect:/settings";
    }

    // 入力検証・パスワード更新・設定画面遷移
    @PostMapping("/password/change")
    public String changePassword(
            @ModelAttribute("form") PasswordChangeForm form,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        String currentPassword = nullToEmpty(form.get_currently_password());
        String newPassword = nullToEmpty(form.get_new_password());
        String confirmPassword = nullToEmpty(form.get_confirm_new_password());

        // 必須項目が未入力の場合
        if (currentPassword.isBlank() || newPassword.isBlank() || confirmPassword.isBlank()) {
            model.addAttribute("errorMessage", "すべての項目を入力してください");
            return "password_change";
        }

        // 半角英数字すべて含む8文字以上を満たしていない場合
        if (!newPassword.matches("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,}$")) {
            model.addAttribute("errorMessage", "パスワードは半角英数字すべて含む8文字以上で設定してください");
            return "password_change";
        }

        // 新しいパスワードが現在のパスワードと同じだった場合
        if (newPassword.equals(currentPassword)) {
            model.addAttribute("errorMessage", "新しいパスワードは現在のパスワードとは違うものを設定してください");
            return "password_change";
        }

        // 新しいパスワードと新しいパスワード（確認）が一致しない場合
        if (!newPassword.equals(confirmPassword)) {
            model.addAttribute("errorMessage", "パスワードが一致しません");
            return "password_change";
        }

        try {
            // 現在のパスワード確認後にパスワードを更新
            Object idTokenObj = session.getAttribute("idToken");
            String idToken = idTokenObj == null ? "" : idTokenObj.toString();

            boolean updated = authClient.changePassword(idToken, currentPassword, newPassword);
            if (!updated) {
                model.addAttribute("errorMessage", "現在のパスワードが間違っています");
                return "password_change";
            }

            // 設定画面に遷移し、スナックバーで「パスワードを変更しました」と表示
            redirectAttributes.addFlashAttribute("snackbarMessage", "パスワードを変更しました");
            return "redirect:/settings";

        } catch (Exception e) {
            // パスワードの更新に失敗した場合
            model.addAttribute("errorMessage", "パスワードの更新に失敗しました");
            return "password_change";
        }
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}