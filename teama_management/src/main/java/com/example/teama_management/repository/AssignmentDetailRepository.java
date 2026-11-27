package com.example.teama_management.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
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

        // ログインユーザーが課題を表示できるか確認する
        if (!hasAssignmentPermission(assignmentDoc, loginUid)) {
            return null;
        }

        // 課題の種類を取得する
        String targetType = assignmentDoc.getString("targetType");

        // 担当者IDを取得する
        List<String> assigneeIds = getStringList(
                assignmentDoc.get("assigneeIds"));

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

        // 課題のステータスを取得する
        String status;

        if ("team".equals(targetType)) {
            // チーム課題の場合は課題のステータスを取得する
            status = valueOrDefault(
                    assignmentDoc.getString("status"), "notStarted");
        } else {
            // 個人課題の場合はユーザーごとの進捗状況を取得する
            status = findUserProgressStatus(assignmentId, loginUid);
        }

        assignment.put("status", status);
        assignment.put("statusLabel", statusLabel(status));

        // 再提出理由を取得する
        String submissionId = "team".equals(targetType)
                ? assignmentId + "_team"
                : assignmentId + "_" + loginUid;

        DocumentSnapshot submissionDoc = firestore.collection("submissions")
                .document(submissionId)
                .get()
                .get();

        String resubmissionReason = "";
        String submittedByName = "";

        if (submissionDoc.exists()
                && ("resubmit".equals(status) || "再提出".equals(status))) {

            // 再提出理由を取得する
            resubmissionReason = valueOrDefault(
                    submissionDoc.getString("resubmissionReason"), "");

            // 課題を作成したユーザーIDを取得する
            String createdBy = assignmentDoc.getString("createdBy");

            // 課題を作成したユーザーの名前を取得する
            if (createdBy != null && !createdBy.isBlank()) {
                DocumentSnapshot createdUserDoc = firestore.collection("users")
                        .document(createdBy)
                        .get()
                        .get();

                if (createdUserDoc.exists()) {
                    submittedByName = valueOrDefault(
                            createdUserDoc.getString("username"), "ユーザー名なし");
                }
            }
        }

        assignment.put("resubmissionReason", resubmissionReason);
        assignment.put("submittedByName", submittedByName);
        assignment.put("showResubmissionReason",
                !resubmissionReason.isBlank());

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

        // ログインユーザーが課題を変更できるか確認する
        if (!hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("変更する権限がありません");
        }

        String targetType = assignmentDoc.getString("targetType");

        if ("team".equals(targetType)) {
            // チーム課題の場合は課題のステータスを更新する
            firestore.collection("assignments")
                    .document(assignmentId)
                    .update("status", status)
                    .get();

        } else {
            // 個人課題の場合は既存の進捗データを取得する
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

        // ログインユーザーが課題を提出できるか確認する
        if (!hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("提出する権限がありません");
        }

        // 課題の種類を取得する
        String targetType = assignmentDoc.getString("targetType");

        // 提出日時を取得する
        Timestamp submittedAt = Timestamp.now();

        if ("team".equals(targetType)) {
            // チーム課題の場合は課題のステータスを承認待ちに変更する
            firestore.collection("assignments")
                    .document(assignmentId)
                    .update(
                            "status", "waitingApproval",
                            "submittedAt", submittedAt)
                    .get();

        } else {
            // 個人課題の場合は既存の進捗データを取得する
            QuerySnapshot result = firestore.collection("taskProgress")
                    .whereEqualTo("assignmentId", assignmentId)
                    .whereEqualTo("userId", loginUid)
                    .get()
                    .get();

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

        // 提出情報を登録する
        String submissionId = "team".equals(targetType)
                ? assignmentId + "_team"
                : assignmentId + "_" + loginUid;

        Map<String, Object> submissionData = new HashMap<>();
        submissionData.put("assignmentId", assignmentId);
        submissionData.put("targetType", targetType);
        submissionData.put("userId",
                "team".equals(targetType) ? "team" : loginUid);
        submissionData.put("submittedBy", loginUid);
        submissionData.put("status", "waitingApproval");
        submissionData.put("submittedAt", submittedAt);

        // 前回の再提出理由を削除する
        submissionData.put("resubmissionReason", FieldValue.delete());

        firestore.collection("submissions")
                .document(submissionId)
                .set(submissionData, SetOptions.merge())
                .get();
    }

    // コメント一覧を取得する
    public List<Map<String, Object>> findComments(
            String assignmentId, String loginUid) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("表示する権限がありません");
        }

        // コメントを取得する
        QuerySnapshot result = firestore.collection("comments")
                .whereEqualTo("ownerType", "assignment")
                .whereEqualTo("ownerId", assignmentId)
                .get()
                .get();

        List<Map<String, Object>> comments = new ArrayList<>();

        for (DocumentSnapshot commentDoc : result.getDocuments()) {

            Map<String, Object> comment = new HashMap<>();

            String userId = commentDoc.getString("userId");
            String username = "ユーザー";

            // 投稿者名を取得する
            if (userId != null && !userId.isBlank()) {
                DocumentSnapshot userDoc = firestore.collection("users")
                        .document(userId)
                        .get()
                        .get();

                if (userDoc.exists()) {
                    username = valueOrDefault(
                            userDoc.getString("username"), "ユーザー");
                }
            }

            comment.put("commentId", commentDoc.getId());
            comment.put("userName", username);
            comment.put("body",
                    valueOrDefault(commentDoc.getString("body"), ""));
            comment.put("createdAtText",
                    formatDate(commentDoc.getTimestamp("createdAt")));

            comments.add(comment);
        }

        return comments;
    }

    // コメントを投稿する
    public void addComment(
            String assignmentId, String loginUid, String body) throws Exception {

        // コメントが入力されているか確認する
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("コメントを入力してください");
        }

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("投稿する権限がありません");
        }

        // コメント情報を登録する
        Map<String, Object> commentData = new HashMap<>();
        commentData.put("ownerType", "assignment");
        commentData.put("ownerId", assignmentId);
        commentData.put("userId", loginUid);
        commentData.put("body", body.trim());
        commentData.put("createdAt", Timestamp.now());

        firestore.collection("comments")
                .document()
                .set(commentData)
                .get();
    }

    // チェックリスト一覧を取得する
    public List<Map<String, Object>> findChecklist(
            String assignmentId, String loginUid) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("表示する権限がありません");
        }

        QuerySnapshot result = firestore.collection("checklist")
                .whereEqualTo("assignmentId", assignmentId)
                .get()
                .get();

        List<Map<String, Object>> checklists = new ArrayList<>();

        for (DocumentSnapshot checklistDoc : result.getDocuments()) {

            Map<String, Object> checklist = new HashMap<>();

            checklist.put("checklistId", checklistDoc.getId());
            checklist.put("title",
                    valueOrDefault(checklistDoc.getString("title"), ""));
            checklist.put("isChecked",
                    Boolean.TRUE.equals(checklistDoc.getBoolean("isChecked")));

            checklists.add(checklist);
        }

        return checklists;
    }

    // チェックリストを追加する
    public void addChecklist(
            String assignmentId, String loginUid, String title) throws Exception {

        // チェックリスト名が入力されているか確認する
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("チェックリスト名を入力してください");
        }

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("追加する権限がありません");
        }

        // チェックリスト情報を登録する
        Map<String, Object> checklistData = new HashMap<>();
        checklistData.put("assignmentId", assignmentId);
        checklistData.put("title", title.trim());
        checklistData.put("isChecked", false);
        checklistData.put("createdBy", loginUid);
        checklistData.put("createdAt", Timestamp.now());
        checklistData.put("updatedAt", Timestamp.now());

        firestore.collection("checklist")
                .document()
                .set(checklistData)
                .get();
    }

    // チェックリストの状態を変更する
    public void updateChecklistStatus(
            String assignmentId, String checklistId,
            String loginUid, boolean isChecked) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("変更する権限がありません");
        }

        // チェックリスト情報を取得する
        DocumentSnapshot checklistDoc = firestore.collection("checklist")
                .document(checklistId)
                .get()
                .get();

        if (!checklistDoc.exists()
                || !assignmentId.equals(checklistDoc.getString("assignmentId"))) {
            throw new IllegalArgumentException("チェックリストが見つかりません");
        }

        // チェック状態を更新する
        firestore.collection("checklist")
                .document(checklistId)
                .update(
                        "isChecked", isChecked,
                        "updatedAt", Timestamp.now())
                .get();
    }

    // チェックリストを削除する
    public void deleteChecklist(
            String assignmentId, String checklistId,
            String loginUid) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()
                || !hasAssignmentPermission(assignmentDoc, loginUid)) {
            throw new IllegalArgumentException("削除する権限がありません");
        }

        // チェックリスト情報を取得する
        DocumentSnapshot checklistDoc = firestore.collection("checklist")
                .document(checklistId)
                .get()
                .get();

        if (!checklistDoc.exists()
                || !assignmentId.equals(checklistDoc.getString("assignmentId"))) {
            throw new IllegalArgumentException("チェックリストが見つかりません");
        }

        // チェックリストを削除する
        firestore.collection("checklist")
                .document(checklistId)
                .delete()
                .get();
    }

    // 課題を表示・操作できるユーザーか確認する
    private boolean hasAssignmentPermission(
            DocumentSnapshot assignmentDoc, String loginUid) throws Exception {

        String targetType = assignmentDoc.getString("targetType");

        if ("team".equals(targetType)) {
            // チーム課題の場合はチームメンバーか確認する
            String teamId = assignmentDoc.getString("teamId");

            if (teamId == null) {
                return false;
            }

            QuerySnapshot memberResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", loginUid)
                    .get()
                    .get();

            return !memberResult.isEmpty();

        } else {
            // 個人課題の場合は担当者か確認する
            List<String> assigneeIds = getStringList(
                    assignmentDoc.get("assigneeIds"));

            return assigneeIds.contains(loginUid);
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
