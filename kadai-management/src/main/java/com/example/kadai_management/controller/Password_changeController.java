package com.example.kadai_management.controller;
import javax.swing.*;
import java.awt.*;
import java.util.regex.Pattern;

/**
 * 課題管理システム - パスワード変更画面 (Password_change_page.java)
 * 画面設計書（No.1〜16）・ユースケース記述・シーケンス図・画面イメージに基づき実装
 */
public class Password_changeController extends JFrame {

    // No.1〜10 画面項目
    private JLabel titleLabel;                         // No.1 画面タイトル
    private JButton backButton;                        // No.2 戻るボタン
    private JLabel currentPasswordLabel;               // No.3 現在のパスワードラベル
    private JPasswordField _currently_password;        // No.4 現在のパスワード入力欄
    private JButton currentPasswordEyeButton;
    private JLabel newPasswordLabel;                   // No.5 新しいパスワードラベル
    private JPasswordField _new_password;              // No.6 新しいパスワード入力欄
    private JButton newPasswordEyeButton;
    private JLabel newPasswordConfirmLabel;            // No.7 新しいパスワード（確認）ラベル
    private JPasswordField _confirm_new_password;      // No.8 新しいパスワード（確認）入力欄
    private JButton newPasswordConfirmEyeButton;
    private JButton cancelButton;                      // No.9 キャンセルボタン
    private JButton changeButton;                      // No.10 変更ボタン

    // No.11〜16 エラーメッセージラベル（初期値: 非表示）
    private JLabel emptyErrorLabel;                    // No.11 未入力エラーメッセージ
    private JLabel currentPasswordErrorLabel;          // No.12 現在のパスワード誤入力エラーメッセージ
    private JLabel newPasswordPolicyErrorLabel;        // No.13 パスワード形式エラーメッセージ
    private JLabel samePasswordErrorLabel;             // No.14 新旧パスワード重複エラーメッセージ
    private JLabel passwordMismatchErrorLabel;         // No.15 パスワード不一致エラーメッセージ
    private JLabel updateFailedErrorLabel;             // No.16 パスワード更新失敗エラーメッセージ

    private boolean isCurrentVisible = false;
    private boolean isNewVisible = false;
    private boolean isConfirmVisible = false;
    private char defaultEchoChar;

    private final PasswordChangeController controller;
    private final String currentUserId;

    // 設定画面の遷移先プレースホルダー（シーケンス図 SettingsPage 対応）
    public static class SettingsPage extends JFrame {
        public SettingsPage(String userId, String snackbarMessage) {
            setTitle("課題管理システム - 設定");
            setSize(420, 520);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setLayout(new BorderLayout());

            JLabel infoLabel = new JLabel("設定画面（ユーザーID：" + userId + "）", SwingConstants.CENTER);
            infoLabel.setFont(new Font("SansSerif", Font.BOLD, 14));
            add(infoLabel, BorderLayout.CENTER);

            if (snackbarMessage != null && !snackbarMessage.isEmpty()) {
                JPanel snackbarPanel = new JPanel(new BorderLayout());
                snackbarPanel.setBackground(new Color(50, 50, 50));
                snackbarPanel.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

                JLabel snackbarLabel = new JLabel(snackbarMessage);
                snackbarLabel.setForeground(Color.WHITE);
                snackbarLabel.setFont(new Font("SansSerif", Font.PLAIN, 12));
                snackbarPanel.add(snackbarLabel, BorderLayout.CENTER);

                add(snackbarPanel, BorderLayout.SOUTH);
            }
        }
    }

    // シーケンス図の Firebase Authentication 連携用サービス（モック）
    public static class FirebaseAuthenticationService {
        private static String storedPassword = "Password123";

        public boolean verifyCurrentPassword(String userId, String currentPassword) {
            return storedPassword.equals(currentPassword);
        }

        public boolean updatePassword(String userId, String newPassword) {
            if ("FailPass1".equals(newPassword)) {
                return false;
            }
            storedPassword = newPassword;
            return true;
        }
    }

    // シーケンス図の PasswordChangeController 相当
    public static class PasswordChangeController {
        private final FirebaseAuthenticationService authService;

        public PasswordChangeController(FirebaseAuthenticationService authService) {
            this.authService = authService;
        }

        public enum ValidationResult {
            VALID,
            EMPTY_FIELDS,
            INVALID_CURRENT_PASSWORD,
            INVALID_NEW_PASSWORD,
            SAME_AS_CURRENT_PASSWORD,
            PASSWORD_MISMATCH
        }

        public ValidationResult validateInput(
                String userId,
                String currentPassword,
                String newPassword,
                String newPasswordConfirm
        ) {
            // No.11 未入力時
            if (currentPassword.isEmpty() || newPassword.isEmpty() || newPasswordConfirm.isEmpty()) {
                return ValidationResult.EMPTY_FIELDS;
            }
            // No.12 現在のパスワード誤入力時
            if (!authService.verifyCurrentPassword(userId, currentPassword)) {
                return ValidationResult.INVALID_CURRENT_PASSWORD;
            }
            // No.13 パスワード形式エラー時（半角英数字すべて含む8文字以上）
            boolean hasLetter = Pattern.compile("[A-Za-z]").matcher(newPassword).find();
            boolean hasDigit = Pattern.compile("[0-9]").matcher(newPassword).find();
            boolean isHalfWidthAlnum = Pattern.matches("^[A-Za-z0-9]+$", newPassword);
            if (newPassword.length() < 8 || !hasLetter || !hasDigit || !isHalfWidthAlnum) {
                return ValidationResult.INVALID_NEW_PASSWORD;
            }
            // No.14 新旧パスワード重複時
            if (newPassword.equals(currentPassword)) {
                return ValidationResult.SAME_AS_CURRENT_PASSWORD;
            }
            // No.15 パスワード不一致時
            if (!newPassword.equals(newPasswordConfirm)) {
                return ValidationResult.PASSWORD_MISMATCH;
            }
            return ValidationResult.VALID;
        }

        public boolean updatePassword(String userId, String newPassword) {
            return authService.updatePassword(userId, newPassword);
        }
    }

    public Password_changeController() {
        this("s12345");
    }

    public Password_changeController(String userId) {
        this.currentUserId = userId;
        this.controller = new PasswordChangeController(new FirebaseAuthenticationService());

        setTitle("課題管理システム - パスワード変更");
        setSize(420, 660);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        initHeader();
        initBody();
    }

    private void initHeader() {
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(new Color(0, 145, 0));
        headerPanel.setPreferredSize(new Dimension(420, 56));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 16, 4, 16));

        JLabel appTitle = new JLabel("課題管理システム", SwingConstants.CENTER);
        appTitle.setForeground(Color.WHITE);
        appTitle.setFont(new Font("SansSerif", Font.BOLD, 16));

        JLabel userIdLabel = new JLabel("ユーザーID：" + currentUserId, SwingConstants.RIGHT);
        userIdLabel.setForeground(Color.WHITE);
        userIdLabel.setFont(new Font("SansSerif", Font.PLAIN, 11));

        headerPanel.add(appTitle, BorderLayout.CENTER);
        headerPanel.add(userIdLabel, BorderLayout.SOUTH);

        add(headerPanel, BorderLayout.NORTH);
    }

    private void initBody() {
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(Color.WHITE);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(18, 28, 24, 28));

        // No.2 戻るボタン + No.1 画面タイトル
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        titleRow.setBackground(Color.WHITE);
        titleRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        backButton = new JButton("←");
        backButton.setFont(new Font("SansSerif", Font.BOLD, 16));
        backButton.setBorderPainted(false);
        backButton.setContentAreaFilled(false);
        backButton.setFocusPainted(false);
        backButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        backButton.setMargin(new Insets(0, 0, 0, 6));
        backButton.addActionListener(e -> eventProcess14_Back());

        titleLabel = new JLabel("パスワード変更");
        titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

        titleRow.add(backButton);
        titleRow.add(titleLabel);
        mainPanel.add(titleRow);
        mainPanel.add(Box.createVerticalStrut(20));

        // No.3 & No.4 現在のパスワード
        currentPasswordLabel = createFieldLabel("現在のパスワード");
        _currently_password = new JPasswordField();
        defaultEchoChar = _currently_password.getEchoChar();
        currentPasswordEyeButton = createEyeButton();
        currentPasswordEyeButton.addActionListener(e -> {
            isCurrentVisible = !isCurrentVisible;
            _currently_password.setEchoChar(isCurrentVisible ? (char) 0 : defaultEchoChar);
        });
        currentPasswordErrorLabel = createErrorLabel("現在のパスワードが間違っています");

        mainPanel.add(currentPasswordLabel);
        mainPanel.add(Box.createVerticalStrut(6));
        mainPanel.add(createPasswordFieldPanel(_currently_password, currentPasswordEyeButton));
        mainPanel.add(currentPasswordErrorLabel);
        mainPanel.add(Box.createVerticalStrut(14));

        // No.5 & No.6 新しいパスワード
        newPasswordLabel = createFieldLabel("新しいパスワード");
        _new_password = new JPasswordField();
        newPasswordEyeButton = createEyeButton();
        newPasswordEyeButton.addActionListener(e -> {
            isNewVisible = !isNewVisible;
            _new_password.setEchoChar(isNewVisible ? (char) 0 : defaultEchoChar);
        });
        newPasswordPolicyErrorLabel = createErrorLabel("パスワードは半角英数字すべて含む8文字以上で設定してください");
        samePasswordErrorLabel = createErrorLabel("新しいパスワードは現在のパスワードとは違うものを設定してください");

        mainPanel.add(newPasswordLabel);
        mainPanel.add(Box.createVerticalStrut(6));
        mainPanel.add(createPasswordFieldPanel(_new_password, newPasswordEyeButton));
        mainPanel.add(newPasswordPolicyErrorLabel);
        mainPanel.add(samePasswordErrorLabel);
        mainPanel.add(Box.createVerticalStrut(14));

        // No.7 & No.8 新しいパスワード（確認）
        newPasswordConfirmLabel = createFieldLabel("新しいパスワード（確認）");
        _confirm_new_password = new JPasswordField();
        newPasswordConfirmEyeButton = createEyeButton();
        newPasswordConfirmEyeButton.addActionListener(e -> {
            isConfirmVisible = !isConfirmVisible;
            _confirm_new_password.setEchoChar(isConfirmVisible ? (char) 0 : defaultEchoChar);
        });

        emptyErrorLabel = createErrorLabel("すべての項目を入力してください");
        passwordMismatchErrorLabel = createErrorLabel("パスワードが一致しません");
        updateFailedErrorLabel = createErrorLabel("パスワードの更新に失敗しました");

        mainPanel.add(newPasswordConfirmLabel);
        mainPanel.add(Box.createVerticalStrut(6));
        mainPanel.add(createPasswordFieldPanel(_confirm_new_password, newPasswordConfirmEyeButton));
        mainPanel.add(Box.createVerticalStrut(4));
        mainPanel.add(emptyErrorLabel);
        mainPanel.add(passwordMismatchErrorLabel);
        mainPanel.add(updateFailedErrorLabel);
        mainPanel.add(Box.createVerticalStrut(26));

        // No.9 キャンセルボタン / No.10 変更ボタン
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        buttonRow.setBackground(Color.WHITE);
        buttonRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        cancelButton = new JButton("キャンセル");
        cancelButton.setBackground(new Color(208, 0, 0));
        cancelButton.setForeground(Color.WHITE);
        cancelButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        cancelButton.setFocusPainted(false);
        cancelButton.setBorderPainted(false);
        cancelButton.setOpaque(true);
        cancelButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        cancelButton.setPreferredSize(new Dimension(135, 42));
        cancelButton.addActionListener(e -> eventProcess15_Cancel());

        changeButton = new JButton("変更する");
        changeButton.setBackground(new Color(0, 136, 0));
        changeButton.setForeground(Color.WHITE);
        changeButton.setFont(new Font("SansSerif", Font.BOLD, 14));
        changeButton.setFocusPainted(false);
        changeButton.setBorderPainted(false);
        changeButton.setOpaque(true);
        changeButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        changeButton.setPreferredSize(new Dimension(150, 42));
        changeButton.addActionListener(e -> eventProcess16_Change());

        buttonRow.add(cancelButton);
        buttonRow.add(changeButton);
        mainPanel.add(buttonRow);

        add(mainPanel, BorderLayout.CENTER);
    }

    private JLabel createFieldLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("SansSerif", Font.PLAIN, 13));
        label.setForeground(new Color(40, 40, 40));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    private JPanel createPasswordFieldPanel(JPasswordField field, JButton eyeButton) {
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBackground(Color.WHITE);
        wrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        wrapper.setPreferredSize(new Dimension(340, 38));
        wrapper.setBorder(BorderFactory.createLineBorder(new Color(180, 180, 180), 1));
        wrapper.setAlignmentX(Component.LEFT_ALIGNMENT);

        field.setFont(new Font("SansSerif", Font.PLAIN, 14));
        field.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 4));

        wrapper.add(field, BorderLayout.CENTER);
        wrapper.add(eyeButton, BorderLayout.EAST);
        return wrapper;
    }

    private JButton createEyeButton() {
        JButton btn = new JButton("👁");
        btn.setFont(new Font("SansSerif", Font.PLAIN, 12));
        btn.setPreferredSize(new Dimension(36, 36));
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    private JLabel createErrorLabel(String message) {
        JLabel label = new JLabel("※" + message);
        label.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label.setForeground(new Color(210, 40, 40));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        label.setBorder(BorderFactory.createEmptyBorder(2, 2, 0, 0));
        label.setVisible(false);
        return label;
    }

    private void clearErrors() {
        emptyErrorLabel.setVisible(false);
        currentPasswordErrorLabel.setVisible(false);
        newPasswordPolicyErrorLabel.setVisible(false);
        samePasswordErrorLabel.setVisible(false);
        passwordMismatchErrorLabel.setVisible(false);
        updateFailedErrorLabel.setVisible(false);
    }

    /**
     * イベント処理⑭: 1つ前の画面に遷移する
     */
    private void eventProcess14_Back() {
        new SettingsPage(currentUserId, null).setVisible(true);
        this.dispose();
    }

    /**
     * イベント処理⑮: 入力内容を破棄し、設定画面へ戻る
     */
    private void eventProcess15_Cancel() {
        _currently_password.setText("");
        _new_password.setText("");
        _confirm_new_password.setText("");
        clearErrors();
        new SettingsPage(currentUserId, null).setVisible(true);
        this.dispose();
    }

    /**
     * イベント処理⑯: パスワードを変更し、設定画面へ戻る
     */
    private void eventProcess16_Change() {
        clearErrors();

        String currentPassword = new String(_currently_password.getPassword());
        String newPassword = new String(_new_password.getPassword());
        String newPasswordConfirm = new String(_confirm_new_password.getPassword());

        PasswordChangeController.ValidationResult result =
                controller.validateInput(currentUserId, currentPassword, newPassword, newPasswordConfirm);

        switch (result) {
            case EMPTY_FIELDS:
                emptyErrorLabel.setVisible(true);
                return;
            case INVALID_CURRENT_PASSWORD:
                currentPasswordErrorLabel.setVisible(true);
                return;
            case INVALID_NEW_PASSWORD:
                newPasswordPolicyErrorLabel.setVisible(true);
                return;
            case SAME_AS_CURRENT_PASSWORD:
                samePasswordErrorLabel.setVisible(true);
                return;
            case PASSWORD_MISMATCH:
                passwordMismatchErrorLabel.setVisible(true);
                return;
            case VALID:
                break;
        }

        boolean updated = controller.updatePassword(currentUserId, newPassword);
        if (updated) {
            new SettingsPage(currentUserId, "パスワードを変更しました").setVisible(true);
            this.dispose();
        } else {
            updateFailedErrorLabel.setVisible(true);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new Password_changeController("s12345").setVisible(true);
        });
    }
}