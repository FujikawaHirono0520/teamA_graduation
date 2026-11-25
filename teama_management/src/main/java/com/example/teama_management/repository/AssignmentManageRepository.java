package com.example.teama_management.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import org.springframework.stereotype.Repository;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class AssignmentManageRepository {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("M/d HH:mm");

    private final Firestore firestore;

    public AssignmentManageRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // 課題管理画面に表示する課題情報を取得する
    public Map<String, Object> findManageData(
            String assignmentId, String loginUid) throws Exception {

        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            return null;
        }

        String teamId = assignmentDoc.getString("teamId");
        if (teamId == null || !isLeader(teamId, loginUid)) {
            return null;
        }

        Map<String, Object> assignment = new HashMap<>();
        assignment.put("id", assignmentId);
        assignment.put("title", valueOrDefault(assignmentDoc.getString("title"), "課題名なし"));
        String targetName;
        if ("team".equals(assignmentDoc.getString("targetType"))) {
            DocumentSnapshot teamDoc = firestore.collection("teams")
                    .document(teamId)
                    .get()
                    .get();
            String teamName = teamDoc.exists()
                    ? valueOrDefault(teamDoc.getString("teamName"), "チーム")
                    : "チーム";
            targetName = teamName + " 全員";
        } else {
            targetName = "個人";
        }
        assignment.put("targetName", targetName);
        assignment.put("priorityLabel", priorityLabel(assignmentDoc.getString("priority")));
        assignment.put("dueDateText", formatDate(assignmentDoc.getTimestamp("dueDate")));

        List<String> assigneeIds = getStringList(assignmentDoc.get("assigneeIds"));
        List<Map<String, String>> userProgress = new ArrayList<>();

        for (String userId : assigneeIds) {
            DocumentSnapshot userDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .get();

            if (!userDoc.exists()) {
                continue;
            }

            String username = valueOrDefault(userDoc.getString("username"), userId);
            String status = findUserProgressStatus(assignmentId, userId);

            Map<String, String> user = new HashMap<>();
            user.put("id", userId);
            user.put("name", username);
            user.put("statusLabel", progressLabel(status));
            user.put("statusClass", progressClass(status));
            userProgress.add(user);
        }

        assignment.put("userProgress", userProgress);
        return assignment;
    }

    // 課題の削除を行う（リーダーのみ）
    public boolean deleteAssignment(String assignmentId, String loginUid)
            throws Exception {

        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            return false;
        }

        String teamId = assignmentDoc.getString("teamId");
        if (teamId == null || !isLeader(teamId, loginUid)) {
            return false;
        }

        // 課題に紐づく進捗データも削除する
        QuerySnapshot progressDocs = firestore.collection("taskProgress")
                .whereEqualTo("assignmentId", assignmentId)
                .get()
                .get();

        for (DocumentSnapshot progressDoc : progressDocs.getDocuments()) {
            progressDoc.getReference().delete().get();
        }

        assignmentDoc.getReference().delete().get();
        return true;
    }

    // ログインユーザーが対象チームのリーダーか確認する
    private boolean isLeader(String teamId, String loginUid) throws Exception {
        QuerySnapshot result = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", loginUid)
                .whereEqualTo("role", "leader")
                .get()
                .get();
        return !result.isEmpty();
    }

    // ユーザーごとの進捗状況を取得する
    // taskProgressのフィールドは assignmentId / userId / status を想定
    private String findUserProgressStatus(String assignmentId, String userId)
            throws Exception {
        QuerySnapshot result = firestore.collection("taskProgress")
                .whereEqualTo("assignmentId", assignmentId)
                .whereEqualTo("userId", userId)
                .get()
                .get();

        if (result.isEmpty()) {
            return "notStarted";
        }

        String status = result.getDocuments().get(0).getString("status");
        return valueOrDefault(status, "notStarted");
    }

    private List<String> getStringList(Object value) {
        List<String> ids = new ArrayList<>();
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String id) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    private String priorityLabel(String priority) {
        if (priority == null) return "－";
        return switch (priority) {
            case "high", "高" -> "高";
            case "medium", "中" -> "中";
            case "low", "低" -> "低";
            default -> priority;
        };
    }

    private String formatDate(Timestamp timestamp) {
        if (timestamp == null) return "－";
        return DATE_FORMAT.format(timestamp.toDate().toInstant().atZone(ZONE));
    }

    private String progressLabel(String status) {
        return switch (status) {
            case "inProgress", "着手中" -> "着手中";
            case "completed", "approved", "完了", "承認済み" -> "完了";
            default -> "未着手";
        };
    }

    private String progressClass(String status) {
        return switch (status) {
            case "inProgress", "着手中" -> "progress-in-progress";
            case "completed", "approved", "完了", "承認済み" -> "progress-completed";
            default -> "progress-not-started";
        };
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
