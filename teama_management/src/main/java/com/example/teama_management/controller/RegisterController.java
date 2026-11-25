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
<<<<<<< HEAD
        model.addAttribute("registerForm", new RegisterForm());
        return "register";
    }

    @GetMapping("/register/back")
    public String backToLogin() {
        return "redirect:/login";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("registerForm") RegisterForm form, Model model) {
        String username = trim(form.get_usernameController());
        String email = trim(form.get_emailController());
        String password = nullToEmpty(form.get_passwordController());
        String confirm = nullToEmpty(form.get_passwordConfirmController());

        // 代替フロー 4a-1 〜 5a-3 の順で検証
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirm.isEmpty()) {
            model.addAttribute("emptyError", "※すべての項目を入力してください");
            return "register";
        }
        if (!email.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            model.addAttribute("emailFormatError", "※メールアドレスを正しく入力してください");
            return "register";
        }
        if (!password.matches("^(?=.*[A-Za-z])(?=.*[0-9])[A-Za-z0-9]{8,}$")) {
            model.addAttribute("passwordPolicyError", "※英数字すべて含みかつ8文字以上でパスワードを設定してください");
            return "register";
        }
        if (!password.equals(confirm)) {
            model.addAttribute("passwordMismatchError", "※パスワードが一致しません");
=======
        model.addAttribute("form", new RegisterForm());
        return "register";
    }

    @PostMapping("/register")
    public String register(@ModelAttribute("form") RegisterForm form, Model model) {
        // 入力内容を検証する
        String error = validate(form);
        if (error != null) {
            model.addAttribute("errorMessage", error);
>>>>>>> origin/master
            return "register";
        }

        try {
<<<<<<< HEAD
            // 基本フロー 6: Firebase Authentication にアカウント登録
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(username);
            UserRecord user = FirebaseAuth.getInstance().createUser(request);

            // Firestore (Users) にユーザー情報を保存
            Map<String, Object> data = new HashMap<>();
            data.put("userId", user.getUid());
            data.put("name", username);
            data.put("email", email);
            data.put("createdAt", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());
            firestore.collection("Users").document(user.getUid()).set(data).get();

            // 基本フロー 7: ログイン画面へ遷移
            return "redirect:/login";
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() == AuthErrorCode.EMAIL_ALREADY_EXISTS) {
                model.addAttribute("emailDuplicateError", "※このメールアドレスは登録済みです");
            } else {
                model.addAttribute("systemError", "※ユーザー登録に失敗しました");
            }
            return "register";
        } catch (Exception e) {
            model.addAttribute("systemError", "※ユーザー登録に失敗しました");
            return "register";
        }
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private String nullToEmpty(String s) {
        return s == null ? "" : s;
=======
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
>>>>>>> origin/master
    }
}