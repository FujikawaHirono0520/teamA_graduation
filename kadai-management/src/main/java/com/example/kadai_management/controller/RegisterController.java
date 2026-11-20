package com.example.kadai_management.controller;
import javax.swing.*;
import java.awt.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 新規登録処理を制御するコントローラークラス (RegisterController.java)
 * シーケンス図（「新規登録する」）・ユースケース記述・テーブル定義書（Users）準拠
 */
public class RegisterController {

    private final FirebaseAuthenticationService authService;
    private final FirestoreUserRepository userRepository;

    public enum ValidationResult {
        VALID,
        EMPTY_FIELDS,
        INVALID_EMAIL,
        INVALID_PASSWORD,
        PASSWORD_MISMATCH,
        DUPLICATE_EMAIL
    }

    // Userモデル（クラス図・テーブル定義書 Users 準拠）
    public static class User {
        public String userId;
        public String name;
        public String email;
        public Timestamp createdAt;
        public Timestamp updatedAt;

        public User(String userId, String name, String email, Timestamp createdAt, Timestamp updatedAt) {
            this.userId = userId;
            this.name = name;
            this.email = email;
            this.createdAt = createdAt;
            this.updatedAt = updatedAt;
        }
    }

    // Firebase Authentication 連携用サービス（モック）
    public static class FirebaseAuthenticationService {
        private static final Set<String> registeredEmails = new HashSet<>();

        static {
            registeredEmails.add("existing@example.com");
        }

        public boolean isEmailRegistered(String email) {
            return registeredEmails.contains(email);
        }

        public String registerAccount(String email, String password) throws Exception {
            if ("error@example.com".equalsIgnoreCase(email)) {
                throw new Exception("Firebase Authentication registration failed.");
            }
            registeredEmails.add(email);
            return "uid_" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    // Firestore（Usersコレクション）保存用リポジトリ（モック）
    public static class FirestoreUserRepository {
        public void saveUser(User user) throws Exception {
            if (user == null || user.userId == null || user.userId.isEmpty()) {
                throw new Exception("Firestore save failed.");
            }
        }
    }

    // ログイン画面の遷移先プレースホルダー（シーケンス図 LoginPage 対応）
    public static class LoginPage extends JFrame {
        public LoginPage(String registeredEmail) {
            setTitle("課題管理システム - ログイン");
            setSize(400, 300);
            setLocationRelativeTo(null);
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JLabel label = new JLabel("ログイン画面（登録メール: " + registeredEmail + "）", SwingConstants.CENTER);
            add(label, BorderLayout.CENTER);
        }
    }

    public RegisterController() {
        this(new FirebaseAuthenticationService(), new FirestoreUserRepository());
    }

    public RegisterController(FirebaseAuthenticationService authService, FirestoreUserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    /**
     * 代替フロー 4a-1〜5a-4 / シーケンス図 7〜12 の入力検証
     */
    public ValidationResult validateInput(String username, String email, String password, String passwordConfirm) {
        String safeUsername = username == null ? "" : username.trim();
        String safeEmail = email == null ? "" : email.trim();
        String safePassword = password == null ? "" : password;
        String safePasswordConfirm = passwordConfirm == null ? "" : passwordConfirm;

        // 1. 未入力チェック
        if (safeUsername.isEmpty() || safeEmail.isEmpty() || safePassword.isEmpty() || safePasswordConfirm.isEmpty()) {
            return ValidationResult.EMPTY_FIELDS;
        }
        // 2. メールアドレス形式チェック
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        if (!Pattern.matches(emailRegex, safeEmail)) {
            return ValidationResult.INVALID_EMAIL;
        }
        // 3. パスワード条件チェック（英数字すべて含むかつ8文字以上）
        boolean hasLetter = Pattern.compile("[A-Za-z]").matcher(safePassword).find();
        boolean hasDigit = Pattern.compile("[0-9]").matcher(safePassword).find();
        if (safePassword.length() < 8 || !hasLetter || !hasDigit) {
            return ValidationResult.INVALID_PASSWORD;
        }
        // 4. パスワード一致チェック
        if (!safePassword.equals(safePasswordConfirm)) {
            return ValidationResult.PASSWORD_MISMATCH;
        }
        // 5. 登録済みメールアドレスチェック
        if (authService.isEmailRegistered(safeEmail)) {
            return ValidationResult.DUPLICATE_EMAIL;
        }
        return ValidationResult.VALID;
    }

    /**
     * 基本フロー 6 / シーケンス図 13〜16: アカウント登録と Firestore 保存
     */
    public User register(String username, String email, String password) throws Exception {
        String uid = authService.registerAccount(email.trim(), password);
        Timestamp now = Timestamp.from(Instant.now());
        User user = new User(uid, username.trim(), email.trim(), now, now);
        userRepository.saveUser(user);
        return user;
    }

    /**
     * 新規登録画面（UI）側クラス
     */
    public static class RegisterPage extends JFrame {
        private JLabel titleLabel;
        private JButton backButton;
        private JLabel usernameLabel;
        private JTextField _usernameController;
        private JLabel emailLabel;
        private JTextField _emailController;
        private JLabel passwordLabel;
        private JPasswordField _passwordController;
        private JButton passwordEyeButton;
        private JLabel passwordConfirmLabel;
        private JPasswordField _passwordConfirmController;
        private JButton passwordConfirmEyeButton;
        private JButton registerButton;

        private JLabel emptyErrorLabel;
        private JLabel emailFormatErrorLabel;
        private JLabel passwordPolicyErrorLabel;
        private JLabel passwordMismatchErrorLabel;
        private JLabel emailDuplicateErrorLabel;
        private JLabel systemErrorLabel;

        private boolean isPasswordVisible = false;
        private boolean isPasswordConfirmVisible = false;
        private char defaultEchoChar;

        private final RegisterController controller;

        public RegisterPage() {
            this(new RegisterController());
        }

        public RegisterPage(RegisterController controller) {
            this.controller = controller;

            setTitle("課題管理システム - 新規登録");
            setSize(420, 680);
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

            JLabel userIdLabel = new JLabel("ユーザーID：oooo", SwingConstants.RIGHT);
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
            backButton.addActionListener(e -> onBackClicked());

            titleLabel = new JLabel("新規登録");
            titleLabel.setFont(new Font("SansSerif", Font.BOLD, 16));

            titleRow.add(backButton);
            titleRow.add(titleLabel);
            mainPanel.add(titleRow);
            mainPanel.add(Box.createVerticalStrut(20));

            usernameLabel = createFieldLabel("ユーザー名");
            _usernameController = createTextField();
            mainPanel.add(usernameLabel);
            mainPanel.add(Box.createVerticalStrut(6));
            mainPanel.add(_usernameController);
            mainPanel.add(Box.createVerticalStrut(14));

            emailLabel = createFieldLabel("メールアドレス");
            _emailController = createTextField();
            emailFormatErrorLabel = createErrorLabel("メールアドレスを正しく入力してください");
            emailDuplicateErrorLabel = createErrorLabel("このメールアドレスは登録済みです");
            mainPanel.add(emailLabel);
            mainPanel.add(Box.createVerticalStrut(6));
            mainPanel.add(_emailController);
            mainPanel.add(emailFormatErrorLabel);
            mainPanel.add(emailDuplicateErrorLabel);
            mainPanel.add(Box.createVerticalStrut(14));

            passwordLabel = createFieldLabel("パスワード");
            _passwordController = new JPasswordField();
            defaultEchoChar = _passwordController.getEchoChar();
            passwordEyeButton = createEyeButton();
            passwordEyeButton.addActionListener(e -> togglePasswordVisibility());
            mainPanel.add(passwordLabel);
            mainPanel.add(Box.createVerticalStrut(6));
            mainPanel.add(createPasswordFieldPanel(_passwordController, passwordEyeButton));
            mainPanel.add(Box.createVerticalStrut(14));

            passwordConfirmLabel = createFieldLabel("パスワード（確認用）");
            _passwordConfirmController = new JPasswordField();
            passwordConfirmEyeButton = createEyeButton();
            passwordConfirmEyeButton.addActionListener(e -> togglePasswordConfirmVisibility());

            emptyErrorLabel = createErrorLabel("すべての項目を入力してください");
            passwordPolicyErrorLabel = createErrorLabel("英数字すべて含むかつ8文字以上でパスワードを設定してください");
            passwordMismatchErrorLabel = createErrorLabel("パスワードが一致しません");
            systemErrorLabel = createErrorLabel("ユーザー登録に失敗しました");

            mainPanel.add(passwordConfirmLabel);
            mainPanel.add(Box.createVerticalStrut(6));
            mainPanel.add(createPasswordFieldPanel(_passwordConfirmController, passwordConfirmEyeButton));
            mainPanel.add(Box.createVerticalStrut(4));
            mainPanel.add(emptyErrorLabel);
            mainPanel.add(passwordPolicyErrorLabel);
            mainPanel.add(passwordMismatchErrorLabel);
            mainPanel.add(systemErrorLabel);
            mainPanel.add(Box.createVerticalStrut(28));

            registerButton = new JButton("新規登録");
            registerButton.setBackground(new Color(14, 148, 224));
            registerButton.setForeground(Color.WHITE);
            registerButton.setFont(new Font("SansSerif", Font.BOLD, 15));
            registerButton.setFocusPainted(false);
            registerButton.setBorderPainted(false);
            registerButton.setOpaque(true);
            registerButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
            registerButton.setMaximumSize(new Dimension(240, 44));
            registerButton.setPreferredSize(new Dimension(240, 44));
            registerButton.setAlignmentX(Component.CENTER_ALIGNMENT);
            registerButton.addActionListener(e -> onRegisterClicked());

            JPanel buttonWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER));
            buttonWrapper.setBackground(Color.WHITE);
            buttonWrapper.setAlignmentX(Component.LEFT_ALIGNMENT);
            buttonWrapper.add(registerButton);

            mainPanel.add(buttonWrapper);
            add(mainPanel, BorderLayout.CENTER);
        }

        private JLabel createFieldLabel(String text) {
            JLabel label = new JLabel(text);
            label.setFont(new Font("SansSerif", Font.PLAIN, 13));
            label.setForeground(new Color(40, 40, 40));
            label.setAlignmentX(Component.LEFT_ALIGNMENT);
            return label;
        }

        private JTextField createTextField() {
            JTextField field = new JTextField();
            field.setFont(new Font("SansSerif", Font.PLAIN, 14));
            field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
            field.setPreferredSize(new Dimension(340, 38));
            field.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(180, 180, 180), 1),
                    BorderFactory.createEmptyBorder(4, 10, 4, 10)
            ));
            field.setAlignmentX(Component.LEFT_ALIGNMENT);
            return field;
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

        private void togglePasswordVisibility() {
            isPasswordVisible = !isPasswordVisible;
            _passwordController.setEchoChar(isPasswordVisible ? (char) 0 : defaultEchoChar);
        }

        private void togglePasswordConfirmVisibility() {
            isPasswordConfirmVisible = !isPasswordConfirmVisible;
            _passwordConfirmController.setEchoChar(isPasswordConfirmVisible ? (char) 0 : defaultEchoChar);
        }

        private void clearErrors() {
            emptyErrorLabel.setVisible(false);
            emailFormatErrorLabel.setVisible(false);
            passwordPolicyErrorLabel.setVisible(false);
            passwordMismatchErrorLabel.setVisible(false);
            emailDuplicateErrorLabel.setVisible(false);
            systemErrorLabel.setVisible(false);
        }

        private void onBackClicked() {
            new LoginPage(_emailController.getText().trim()).setVisible(true);
            this.dispose();
        }

        private void onRegisterClicked() {
            clearErrors();

            String username = _usernameController.getText().trim();
            String email = _emailController.getText().trim();
            String password = new String(_passwordController.getPassword());
            String passwordConfirm = new String(_passwordConfirmController.getPassword());

            ValidationResult result = controller.validateInput(username, email, password, passwordConfirm);

            switch (result) {
                case EMPTY_FIELDS:
                    emptyErrorLabel.setVisible(true);
                    return;
                case INVALID_EMAIL:
                    emailFormatErrorLabel.setVisible(true);
                    return;
                case INVALID_PASSWORD:
                    passwordPolicyErrorLabel.setVisible(true);
                    return;
                case PASSWORD_MISMATCH:
                    passwordMismatchErrorLabel.setVisible(true);
                    return;
                case DUPLICATE_EMAIL:
                    emailDuplicateErrorLabel.setVisible(true);
                    return;
                case VALID:
                    break;
            }

            try {
                controller.register(username, email, password);
                new LoginPage(email).setVisible(true);
                this.dispose();
            } catch (Exception ex) {
                systemErrorLabel.setVisible(true);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new RegisterPage().setVisible(true);
        });
    }
}