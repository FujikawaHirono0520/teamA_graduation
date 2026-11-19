package com.example.teama_management.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class HomeController {

    @GetMapping("/home")
    public String home(HttpSession session, Model model) {
        if (session.getAttribute("loginUid") == null) {
            return "redirect:/login";   // 未ログインならログイン画面へ
        }
        model.addAttribute("name", session.getAttribute("loginName"));
        return "home";
    }

    // 仮のログアウト（「ログアウトする」のユースケースを作るときに作り直す）
    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}