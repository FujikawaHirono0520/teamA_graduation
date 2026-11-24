package com.example.teama_management.controller;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Controller
public class DevSeedController {

    private final Firestore firestore;

    public DevSeedController(Firestore firestore) {
        this.firestore = firestore;
    }

    @GetMapping("/dev/seed")
    @ResponseBody
    public String seed(HttpSession session) throws Exception {
        String uid = (String) session.getAttribute("loginUid");
        String name = (String) session.getAttribute("loginName");
        if (uid == null || uid.isBlank()) {
            return "ログイン後に実行してください。";
        }
        String displayName = (name == null || name.isBlank()) ? "テストユーザー" : name;

        // ユーザー情報（Users）
        user(uid, displayName, "user@example.com");
        user("dummy-2", "佐藤 健", "sato@example.com");
        user("dummy-3", "田中 太郎", "tanaka@example.com");
        user("dummy-4", "佐藤 一郎", "ichiro@example.com");

        // チーム（Teams）と所属（TeamMember）
        team("team-a", "情報工学A組", "INV-A001", uid);
        team("team-b", "ゼミB", "INV-B001", uid);

        membership("tm-a-" + uid, "team-a", uid, "user");
        membership("tm-b-" + uid, "team-b", uid, "leader");
        membership("tm-b-d2", "team-b", "dummy-2", "user");
        membership("tm-b-d3", "team-b", "dummy-3", "user");
        membership("tm-b-d4", "team-b", "dummy-4", "user");

        // 情報工学A組の課題（Tasks + TeamAssignments + TeamProgress）
        task("a1", "team-a", "学期末アンケート", "approved", uid, 9, 1);
        assign("ta-a1", "a1", "user", uid);
        progress("tp-a1", "a1", uid, "approved", 1);

        task("a2", "team-a", "グループ発表スライド", "inProgress", uid, 3, 0);
        assign("ta-a2", "a2", "user", uid);
        progress("tp-a2", "a2", uid, "inProgress", 0);

        task("a3", "team-a", "第3回レポート課題", "inProgress", uid, 5, 0);
        assign("ta-a3", "a3", "user", uid);
        progress("tp-a3", "a3", uid, "inProgress", 0);

        task("a4", "team-a", "小テスト復習", "notStarted", uid, 7, 0);
        assign("ta-a4", "a4", "user", uid);
        progress("tp-a4", "a4", uid, "notStarted", 0);

        // 他人担当の課題（ユーザー側には出ないことの確認用）
        task("a5", "team-a", "他の人の課題", "notStarted", uid, 4, 0);
        assign("ta-a5", "a5", "user", "dummy-2");
        progress("tp-a5", "a5", "dummy-2", "notStarted", 0);

        // ゼミBの課題（リーダー側で表示・検索確認用）
        task("b1", "team-b", "ゼミ発表資料", "waitingApproval", uid, 6, 2);
        assign("ta-b1", "b1", "user", "dummy-2");

        task("b2", "team-b", "文献レビュー", "approved", uid, 8, 5);
        assign("ta-b2", "b2", "user", "dummy-3");

        task("b3", "team-b", "アンケート集計", "approved", uid, 10, 8);
        assign("ta-b3", "b3", "user", "dummy-4");

        return "設計書に合わせたテストデータを登録しました。/assignments を開いてください。";
    }

    private void user(String userId, String name, String email) throws Exception {
        Map<String, Object> u = new HashMap<>();
        u.put("userId", userId);
        u.put("name", name);
        u.put("email", email);
        u.put("createdAt", FieldValue.serverTimestamp());
        u.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("Users").document(userId).set(u).get();
    }

    private void team(String teamId, String teamName, String inviteCode, String createdBy) throws Exception {
        Map<String, Object> t = new HashMap<>();
        t.put("teamId", teamId);
        t.put("teamName", teamName);
        t.put("inviteCode", inviteCode);
        t.put("createdBy", createdBy);
        t.put("createdAt", FieldValue.serverTimestamp());
        t.put("updatedAt", FieldValue.serverTimestamp());
        firestore.collection("Teams").document(teamId).set(t).get();
    }

    private void membership(String teamMemberId, String teamId, String userId, String role) throws Exception {
        Map<String, Object> m = new HashMap<>();
        m.put("teamMemberId", teamMemberId);
        m.put("teamId", teamId);
        m.put("userId", userId);
        m.put("role", role);
        m.put("joinedAt", FieldValue.serverTimestamp());
        firestore.collection("TeamMember").document(teamMemberId).set(m).get();
    }

    private void task(String taskId, String teamId, String title, String status,
                      String createdBy, int dueDays, int updatedDaysAgo) throws Exception {
        Map<String, Object> t = new HashMap<>();
        t.put("taskId", taskId);
        t.put("teamId", teamId);
        t.put("title", title);
        t.put("description", "");
        t.put("dueDate", Timestamp.of(Date.from(Instant.now().plus(dueDays, ChronoUnit.DAYS))));
        t.put("priority", "medium");
        t.put("status", status);
        t.put("createdBy", createdBy);
        t.put("createdAt", FieldValue.serverTimestamp());
        t.put("updatedAt", Timestamp.of(Date.from(Instant.now().minus(updatedDaysAgo, ChronoUnit.DAYS))));
        firestore.collection("Tasks").document(taskId).set(t).get();
    }

    private void assign(String assignmentId, String taskId, String assigneeType, String assigneeId) throws Exception {
        Map<String, Object> a = new HashMap<>();
        a.put("assignmentId", assignmentId);
        a.put("taskId", taskId);
        a.put("assigneeType", assigneeType);
        a.put("assigneeId", assigneeId);
        a.put("createdAt", FieldValue.serverTimestamp());
        firestore.collection("TeamAssignments").document(assignmentId).set(a).get();
    }

    private void progress(String progressId, String taskId, String userId,
                          String status, int updatedDaysAgo) throws Exception {
        Map<String, Object> p = new HashMap<>();
        p.put("progressId", progressId);
        p.put("taskId", taskId);
        p.put("userId", userId);
        p.put("status", status);
        p.put("updatedAt", Timestamp.of(Date.from(Instant.now().minus(updatedDaysAgo, ChronoUnit.DAYS))));
        firestore.collection("TeamProgress").document(progressId).set(p).get();
    }
}