
package com.example.teama_management.controller;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProfileController {

    private final Firestore firestore;

    public ProfileController(Firestore firestore) {
        this.firestore = firestore;
    }

    // プロフィールを表示する
    @GetMapping("/profile")
    public String showProfile(
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("uid");

        // ログインしていない場合はログイン画面に戻る
        if (uid == null) {
            return "redirect:/login";
        }

        // Firestoreからユーザー情報を取得する
        DocumentSnapshot userDoc = firestore.collection("users")
                .document(uid)
                .get()
                .get();

        // ユーザー情報が存在しない場合
        if (!userDoc.exists()) {
            return "redirect:/login";
        }

        // 画面に表示する情報を設定する
        model.addAttribute("username", userDoc.getString("username"));
        model.addAttribute("uid", uid);

        return "profile";
    }

    // プロフィールを編集する
    @PostMapping("/profile/edit")
    public String editProfile(
            @RequestParam("username") String username,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("uid");

        // ログインしていない場合はログイン画面に戻る
        if (uid == null) {
            return "redirect:/login";
        }

        // 入力内容を確認する
        if (username == null || username.isBlank()) {
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー名を入力してください");

            redirectAttributes.addFlashAttribute("editMode", true);
            redirectAttributes.addFlashAttribute("usernameInput", "");

            return "redirect:/profile";
        }

        // 前後の空白を取り除く
        username = username.trim();

        try {
            // Firebase Authenticationのユーザー名を更新する
            UserRecord.UpdateRequest request =
                    new UserRecord.UpdateRequest(uid)
                            .setDisplayName(username);

            FirebaseAuth.getInstance().updateUser(request);

            // Firestoreのユーザー情報を更新する
            firestore.collection("users")
                    .document(uid)
                    .update(
                            "username", username,
                            "updatedAt", FieldValue.serverTimestamp()
                    )
                    .get();

            // 更新後は設定画面に戻る
            return "redirect:/profile";

        } catch (Exception e) {

            // 更新に失敗した場合
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー名の更新に失敗しました");

            redirectAttributes.addFlashAttribute("editMode", true);
            redirectAttributes.addFlashAttribute(
                    "usernameInput", username);

            return "redirect:/profile";
        }
    }

    // パスワード変更画面を表示する
    @GetMapping("/password_change_page")
    public String showPasswordChange() {
        return "password_change_page";
    }

    // チーム作成画面を表示する
    @GetMapping("/team_create_page")
    public String showTeamCreate() {
        return "team_create_page";
    }

    // 招待コードでチームに参加する画面を表示する
    @GetMapping("/team_join_page")
    public String showTeamJoin() {
        return "team_join_page";
    }

    // ログアウト確認画面を表示する
    @GetMapping("/logout_confirm_dialog")
    public String showLogoutConfirm() {
        return "logout_confirm_dialog";
    }
}
