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
public class AssignmentDetailRepository {

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

    private final Firestore firestore;

    public AssignmentDetailRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // ユーザー側の課題詳細画面に表示する課題情報を取得する
    public Map<String, Object> findDetailData(
            String assignmentId, String loginUid) throws Exception {

        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            return null;
        }

        // ログインユーザーが課題の担当者か確認する
        List<String> assigneeIds = getStringList(
                assignmentDoc.get("assigneeIds"));

        if (!assigneeIds.contains(loginUid)) {
            return null;
        }

        Map<String, Object> assignment = new HashMap<>();

        assignment.put("id", assignmentId);
        assignment.put("title",
                valueOrDefault(assignmentDoc.getString("title"), "課題名なし"));

        // 担当者名を取得する
        List<String> assigneeNames = new ArrayList<>();

        for (String userId : assigneeIds) {
            DocumentSnapshot userDoc = firestore.collection("users")
                    .document(userId)
                    .get()
                    .get();

            if (userDoc.exists()) {
                String username = valueOrDefault(
                        userDoc.getString("username"), userId);
                assigneeNames.add(username);
            }
        }

        String assigneeName = assigneeNames.isEmpty()
                ? "未設定" : String.join(", ", assigneeNames);

        assignment.put("assigneeName", assigneeName);

        // 期限を取得する
        assignment.put("dueDateText",
                formatDate(assignmentDoc.getTimestamp("dueDate")));

        // 優先度を取得する
        assignment.put("priorityLabel",
                priorityLabel(assignmentDoc.getString("priority")));

        // 課題の説明を取得する
        assignment.put("description",
                valueOrDefault(assignmentDoc.getString("description"), "説明はありません"));

        // 添付資料の有無を取得する
        String attachmentUrl = assignmentDoc.getString("attachmentUrl");
        assignment.put("hasAttachment",
                attachmentUrl != null && !attachmentUrl.isBlank());

        // ユーザー個人の進捗状況を取得する
        String status = findUserProgressStatus(assignmentId, loginUid);
        assignment.put("status", status);
        assignment.put("statusLabel", statusLabel(status));

        return assignment;
    }

    // ユーザーごとの進捗状況を取得する
    private String findUserProgressStatus(
            String assignmentId, String userId) throws Exception {

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

    // ユーザーのステータスを更新する
    public void updateStatus(
            String assignmentId, String loginUid, String status) throws Exception {

        // 選択できるステータスか確認する
        if (!"notStarted".equals(status) && !"inProgress".equals(status)) {
            throw new IllegalArgumentException("ステータスが正しくありません");
        }

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            throw new IllegalArgumentException("課題が見つかりません");
        }

        // ログインユーザーが課題の担当者か確認する
        List<String> assigneeIds = getStringList(
                assignmentDoc.get("assigneeIds"));

        if (!assigneeIds.contains(loginUid)) {
            throw new IllegalArgumentException("変更する権限がありません");
        }

        // 既存の進捗データを取得する
        QuerySnapshot result = firestore.collection("taskProgress")
                .whereEqualTo("assignmentId", assignmentId)
                .whereEqualTo("userId", loginUid)
                .get()
                .get();

        Map<String, Object> progressData = new HashMap<>();
        progressData.put("assignmentId", assignmentId);
        progressData.put("userId", loginUid);
        progressData.put("status", status);

        if (result.isEmpty()) {
            // 進捗データがない場合は新規登録する
            firestore.collection("taskProgress")
                    .document()
                    .set(progressData)
                    .get();
        } else {
            // 進捗データがある場合は更新する
            result.getDocuments().get(0).getReference()
                    .update("status", status)
                    .get();
        }
    }

    // 課題を提出する
    public void submitAssignment(
            String assignmentId, String loginUid) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            throw new IllegalArgumentException("課題が見つかりません");
        }

        // ログインユーザーが課題の担当者か確認する
        List<String> assigneeIds = getStringList(
                assignmentDoc.get("assigneeIds"));

        if (!assigneeIds.contains(loginUid)) {
            throw new IllegalArgumentException("提出する権限がありません");
        }

        // 既存の進捗データを取得する
        QuerySnapshot result = firestore.collection("taskProgress")
                .whereEqualTo("assignmentId", assignmentId)
                .whereEqualTo("userId", loginUid)
                .get()
                .get();

        // 提出日時を取得する
        Timestamp submittedAt = Timestamp.now();

        Map<String, Object> progressData = new HashMap<>();
        progressData.put("assignmentId", assignmentId);
        progressData.put("userId", loginUid);
        progressData.put("status", "waitingApproval");
        progressData.put("submittedAt", submittedAt);

        if (result.isEmpty()) {
            // 進捗データがない場合は新規登録する
            firestore.collection("taskProgress")
                    .document()
                    .set(progressData)
                    .get();
        } else {
            // 進捗データがある場合は更新する
            result.getDocuments().get(0).getReference()
                    .update(
                            "status", "waitingApproval",
                            "submittedAt", submittedAt)
                    .get();
        }
    }

    // 優先度の表示名を取得する
    private String priorityLabel(String priority) {
        if (priority == null) {
            return "－";
        }

        return switch (priority) {
            case "high", "高" -> "高";
            case "medium", "中" -> "中";
            case "low", "低" -> "低";
            default -> priority;
        };
    }

    // ステータスの表示名を取得する
    private String statusLabel(String status) {
        return switch (status) {
            case "inProgress", "着手中" -> "着手中";
            case "waitingApproval", "承認待ち" -> "承認待ち";
            case "resubmit", "再提出" -> "再提出";
            case "approved", "completed", "完了", "承認済み" -> "完了";
            default -> "未着手";
        };
    }

    // Timestampを日付形式に変換する
    private String formatDate(Timestamp timestamp) {
        if (timestamp == null) {
            return "－";
        }

        return DATE_FORMAT.format(
                timestamp.toDate().toInstant().atZone(ZONE));
    }

    // FirestoreのListからStringだけを取得する
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

    // nullや空文字の場合は初期値を返す
    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
