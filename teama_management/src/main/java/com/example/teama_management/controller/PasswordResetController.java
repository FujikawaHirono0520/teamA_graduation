
package com.example.teama_management.controller;

import com.example.teama_management.service.FirebaseAuthRestClient;
import com.example.teama_management.service.FirebaseAuthRestClient.PasswordResetException;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class PasswordResetController {

    private final FirebaseAuthRestClient authClient;

    // 再送信できるまでの待ち時間（60秒）
    private static final long RESEND_WAIT_TIME = 60 * 1000;

    public PasswordResetController(FirebaseAuthRestClient authClient) {
        this.authClient = authClient;
    }

    // パスワード再設定画面を表示する
    @GetMapping("/forgot-password")
    public String showPasswordReset() {
        return "password-reset";
    }

    // パスワード再設定メールを送信する
    @PostMapping("/forgot-password")
    public String sendPasswordReset(
            @RequestParam("email") String email,
            Model model,
            HttpSession session) {

        if (email == null || email.isBlank()) {
            model.addAttribute("errorMessage",
                    "メールアドレスを入力してください");
            return "password-reset";
        }
        else if (!email.matches("^[^@]+@[^@]+\\.[^@]+$")) {
            model.addAttribute("errorMessage",
                    "メールアドレスの形式が正しくありません");
            model.addAttribute("email", email);
            return "password-reset";
        }

        try {
            authClient.sendPasswordResetEmail(email);

            // 送信先メールアドレスと再送信可能時刻を保存
            session.setAttribute("resetEmail", email);
            session.setAttribute(
                    "resendAvailableAt",
                    System.currentTimeMillis() + RESEND_WAIT_TIME);

            return "redirect:/forgot-password/sent";

        } catch (PasswordResetException e) {
            model.addAttribute("errorMessage",
                    "メールの送信に失敗しました。もう一度お試しください");
            model.addAttribute("email", email);
            return "password-reset";
        }
    }

    // 送信完了画面を表示する
    @GetMapping("/forgot-password/sent")
    public String showPasswordResetSent(
            HttpSession session,
            Model model) {

        String email = (String) session.getAttribute("resetEmail");

        if (email == null) {
            return "redirect:/forgot-password";
        }

        long availableAt = (long) session.getAttribute("resendAvailableAt");
        long remaining = Math.max(
                0, availableAt - System.currentTimeMillis());

        // 残り秒数を画面に渡す
        model.addAttribute("email", email);
        model.addAttribute(
                "remainingSeconds",
                (remaining + 999) / 1000);

        return "password-reset-sent";
    }

    // パスワード再設定メールを再送信する
    @PostMapping("/forgot-password/resend")
    public String resendPasswordReset(
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        String email = (String) session.getAttribute("resetEmail");

        if (email == null) {
            return "redirect:/forgot-password";
        }

        long availableAt = (long) session.getAttribute("resendAvailableAt");

        // 60秒経過していない場合は再送信しない
        if (System.currentTimeMillis() < availableAt) {
            return "redirect:/forgot-password/sent";
        }

        try {
            authClient.sendPasswordResetEmail(email);

            // 再送信後、待ち時間を再設定する
            session.setAttribute(
                    "resendAvailableAt",
                    System.currentTimeMillis() + RESEND_WAIT_TIME);

        } catch (PasswordResetException e) {
            redirectAttributes.addFlashAttribute(
                    "resendError",
                    "メールの再送信に失敗しました。時間をおいて再度お試しください");
        }

        return "redirect:/forgot-password/sent";
    }
}