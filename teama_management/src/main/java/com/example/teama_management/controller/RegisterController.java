package com.example.teama_management.controller;

import com.example.teama_management.form.RegisterForm;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
public class RegisterController {

    private final Firestore firestore;

    public RegisterController(Firestore firestore) {
        this.firestore = firestore;
    }

    @GetMapping("/register")
    public String showRegister(Model model) {
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegisterForm form, Model model) {
        // 入力内容を検証する
        String error = validate(form);
        if (error != null) {
            model.addAttribute("errorMessage", error);
            return "register";
        }

        try {
            // Firebase Authentication にアカウントを登録する
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(form.getEmail())
                    .setPassword(form.getPassword())
                    .setDisplayName(form.getUsername());
            UserRecord user = FirebaseAuth.getInstance().createUser(request);

            // Firestore にユーザー情報を保存する
            Map<String, Object> data = new HashMap<>();
            data.put("username", form.getUsername());
            data.put("email", form.getEmail());
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("users").document(user.getUid()).set(data).get();

            return "redirect:/login";
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                error = "このメールアドレスは登録済みです";
            } else {
                error = "ユーザー登録に失敗しました";
            }
        } catch (Exception e) {
            error = "ユーザー登録に失敗しました";
        }
        model.addAttribute("errorMessage", error);
        return "register";
    }

    // ユースケースの順番どおりに検証する。問題なければ null を返す
    private String validate(RegisterForm f) {
        if (isBlank(f.getUsername()) || isBlank(f.getEmail())
                || isBlank(f.getPassword()) || isBlank(f.getConfirmPassword())) {
            return "すべての項目を入力してください";
        }
        if (!f.getEmail().matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            return "メールアドレスを正しく入力してください";
        }
        if (!f.getPassword().matches("^(?=.*[A-Za-z])(?=.*[0-9]).{8,}$")) {
            return "英数字すべて含みかつ8文字以上でパスワードを設定してください";
        }
        if (!f.getPassword().equals(f.getConfirmPassword())) {
            return "パスワードが一致しません";
        }
        return null;
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}