package com.example.teama_management.controller;

import com.example.teama_management.form.LoginForm;
import com.example.teama_management.service.FirebaseAuthRestClient;
import com.example.teama_management.service.FirebaseAuthRestClient.AuthFailedException;
import com.example.teama_management.service.FirebaseAuthRestClient.SignInResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class LoginController {
    private final FirebaseAuthRestClient authClient;
    public LoginController(FirebaseAuthRestClient authClient) {
        this.authClient = authClient;
    }
    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String showLogin(Model model) {
        model.addAttribute("form", new LoginForm());
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @ModelAttribute("form") LoginForm form,
            HttpServletRequest request,
            Model model) {

        // メールアドレスまたはパスワードが未入力の場合
        if (isBlank(form.getEmail()) || isBlank(form.getPassword())) {
            model.addAttribute(
                    "errorMessage",
                    "メールアドレスとパスワードを入力してください");
            return "login";
        }

        try {
            // Firebase Authentication で認証する
            SignInResult result = authClient.signIn(
                    form.getEmail(), form.getPassword());
            // ログイン状態をセッションに保存する
            HttpSession session = request.getSession(true);
            request.changeSessionId();

            session.setAttribute("loginUid", result.uid());

            String name = isBlank(result.displayName())
                    ? result.email()
                    : result.displayName();
            session.setAttribute("loginName", name);
            return "redirect:/home";

        } catch (AuthFailedException e) {

            String message = e.isInvalidCredentials()
                    ? "メールアドレスまたはパスワードが違います"
                    : "ログインに失敗しました";
            model.addAttribute("errorMessage", message);
            return "login";
        }
    }
    // ログアウトする
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        // セッションを破棄する
        session.invalidate();
        // ログイン画面に戻る
        return "redirect:/login";
    }
    // 入力値が空か確認する
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
