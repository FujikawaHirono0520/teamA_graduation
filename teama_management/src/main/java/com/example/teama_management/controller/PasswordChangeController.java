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

    @GetMapping("/password/change")
    public String showPasswordChange(Model model) {
        model.addAttribute("form", new PasswordChangeForm());
        return "password_change";
    }

    @GetMapping("/password/change/cancel")
    public String cancelPasswordChange() {
        return "redirect:/settings";
    }

    @PostMapping("/password/change")
    public String changePassword(
            @ModelAttribute("form") PasswordChangeForm form,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        String error = validate(form);
        if (error != null) {
            model.addAttribute("errorMessage", error);
            return "password_change";
        }

        try {
           
            boolean updated = authClient.changePassword(form.get_currently_password(),form.get_new_password());
            if (!updated) {
                model.addAttribute("errorMessage", "現在のパスワードが間違っています");
                return "password_change";
            }

            redirectAttributes.addFlashAttribute("snackbarMessage", "パスワードを変更しました");
            return "redirect:/settings";
        } catch (Exception e) {
            model.addAttribute("errorMessage", "パスワードの更新に失敗しました");
            return "password_change";
        }
    }

    // ユースケースの順番どおりに検証する。問題なければ null を返す
    private String validate(PasswordChangeForm f) {
        String current = f.get_currently_password();
        String next = f.get_new_password();
        String confirm = f.get_confirm_new_password();

        if (isBlank(current) || isBlank(next) || isBlank(confirm)) {
            return "すべての項目を入力してください";
        }
        if (!next.matches("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,}$")) {
            return "パスワードは半角英数字すべて含む8文字以上で設定してください";
        }
        if (next.equals(current)) {
            return "新しいパスワードは現在のパスワードとは違うものを設定してください";
        }
        if (!next.equals(confirm)) {
            return "パスワードが一致しません";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}