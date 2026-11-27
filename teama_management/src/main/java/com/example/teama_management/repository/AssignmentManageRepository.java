package com.example.teama_management.repository;

import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
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
        assignment.put("title", valueOrDefault(
                assignmentDoc.getString("title"), "課題名なし"));

        // 課題の対象を取得する
        String targetType = assignmentDoc.getString("targetType");
        assignment.put("targetType", targetType);

        String targetName;
        if ("team".equals(targetType)) {
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
        assignment.put("priorityLabel",
                priorityLabel(assignmentDoc.getString("priority")));
        assignment.put("dueDateText",
                formatDate(assignmentDoc.getTimestamp("dueDate")));

        // チーム課題の場合は課題全体の提出状態を確認する
        if ("team".equals(targetType)) {

            String status = valueOrDefault(
                    assignmentDoc.getString("status"), "notStarted");

            // 承認待ちの場合のみボタンを表示する
            assignment.put("showTeamReviewButtons",
                    isWaitingApproval(status));

        } else {

            // 個人課題の場合はユーザーごとの進捗状況を取得する
            List<String> assigneeIds =
                    getStringList(assignmentDoc.get("assigneeIds"));

            List<Map<String, String>> userProgress = new ArrayList<>();

            // 承認待ちのユーザーがいるか確認する
            boolean showProgressTitle = true;

            for (String userId : assigneeIds) {
                DocumentSnapshot userDoc = firestore.collection("users")
                        .document(userId)
                        .get()
                        .get();

                if (!userDoc.exists()) {
                    continue;
                }

                String username = valueOrDefault(
                        userDoc.getString("username"), userId);

                String status = findUserProgressStatus(
                        assignmentId, userId);

                Map<String, String> user = new HashMap<>();
                user.put("id", userId);
                user.put("name", username);
                user.put("status", status);
                user.put("statusLabel", progressLabel(status));
                user.put("statusClass", progressClass(status));

                // 承認待ちのユーザーがいる場合は見出しを非表示にする
                if (isWaitingApproval(status)) {
                    showProgressTitle = false;
                }

                userProgress.add(user);
            }

            assignment.put("userProgress", userProgress);
            assignment.put("showProgressTitle", showProgressTitle);
        }

        return assignment;
    }

    // ユーザーの提出を承認する
    public boolean approveSubmission(
            String assignmentId, String userId, String loginUid) throws Exception {

        return updateSubmissionStatus(
                assignmentId, userId, loginUid, "approved", null);
    }

    // ユーザーに再提出を依頼する
    public boolean requestResubmission(
            String assignmentId, String userId,
            String loginUid, String reason) throws Exception {

        return updateSubmissionStatus(
                assignmentId, userId, loginUid, "resubmit", reason);
    }

    // 提出した課題の状態を更新する
    private boolean updateSubmissionStatus(
            String assignmentId, String userId,
            String loginUid, String newStatus, String reason) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            return false;
        }

        // ログインユーザーがリーダーか確認する
        String teamId = assignmentDoc.getString("teamId");
        if (teamId == null || !isLeader(teamId, loginUid)) {
            return false;
        }

        // 課題の対象種別を取得する
        String targetType = assignmentDoc.getString("targetType");

        // 確認日時を取得する
        Timestamp reviewedAt = Timestamp.now();

        // 提出情報を保存するデータ
        Map<String, Object> submissionData = new HashMap<>();
        submissionData.put("assignmentId", assignmentId);
        submissionData.put("targetType", targetType);
        submissionData.put("status", newStatus);
        submissionData.put("reviewedBy", loginUid);
        submissionData.put("reviewedAt", reviewedAt);

        if (reason != null) {
            submissionData.put("resubmissionReason", reason);
        }

        // チーム課題の場合
        if ("team".equals(targetType)) {

            // 課題全体の提出状態を取得する
            String status = assignmentDoc.getString("status");

            // 承認待ちの場合のみ更新する
            if (!isWaitingApproval(status)) {
                return false;
            }

            // チーム全体の提出状態を更新する
            assignmentDoc.getReference()
                    .update(
                            "status", newStatus,
                            "updatedAt", reviewedAt)
                    .get();

            // チーム課題の提出情報を保存する
            submissionData.put("userId", "team");

            firestore.collection("submissions")
                    .document(assignmentId + "_team")
                    .set(submissionData, SetOptions.merge())
                    .get();

            return true;
        }

        // 個人課題の場合

        // 対象ユーザーが課題の担当者か確認する
        List<String> assigneeIds = getStringList(
                assignmentDoc.get("assigneeIds"));

        if (!assigneeIds.contains(userId)) {
            return false;
        }

        // 対象ユーザーの進捗データを取得する
        QuerySnapshot result = firestore.collection("taskProgress")
                .whereEqualTo("assignmentId", assignmentId)
                .whereEqualTo("userId", userId)
                .get()
                .get();

        if (result.isEmpty()) {
            return false;
        }

        DocumentSnapshot progressDoc = result.getDocuments().get(0);

        // 承認待ちの場合のみ更新する
        if (!isWaitingApproval(progressDoc.getString("status"))) {
            return false;
        }

        // 対象ユーザーの進捗状況だけを更新する
        progressDoc.getReference()
                .update(
                        "status", newStatus,
                        "updatedAt", reviewedAt)
                .get();

        // 個人課題の提出情報を保存する
        submissionData.put("userId", userId);

        firestore.collection("submissions")
                .document(assignmentId + "_" + userId)
                .set(submissionData, SetOptions.merge())
                .get();

        return true;
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

    // 承認待ちか確認する
    private boolean isWaitingApproval(String status) {
        return "waitingApproval".equals(status)
                || "承認待ち".equals(status);
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

        return DATE_FORMAT.format(
                timestamp.toDate().toInstant().atZone(ZONE));
    }

    private String progressLabel(String status) {
        return switch (status) {
            case "inProgress", "着手中" -> "着手中";
            case "waitingApproval", "承認待ち" -> "承認待ち";
            case "resubmit", "再提出" -> "再提出";
            case "completed", "approved", "完了", "承認済み" -> "完了";
            default -> "未着手";
        };
    }

    private String progressClass(String status) {
        return switch (status) {
            case "inProgress", "着手中" -> "progress-in-progress";
            case "waitingApproval", "承認待ち" -> "progress-waiting-approval";
            case "resubmit", "再提出" -> "progress-resubmit";
            case "completed", "approved", "完了", "承認済み" -> "progress-completed";
            default -> "progress-not-started";
        };
    }

    private String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

// コメント一覧を取得する
    public List<Map<String, Object>> findComments(
            String assignmentId, String loginUid) throws Exception {

        // 課題情報を取得する
        DocumentSnapshot assignmentDoc = firestore.collection("assignments")
                .document(assignmentId)
                .get()
                .get();

        if (!assignmentDoc.exists()) {
            throw new IllegalArgumentException("課題が見つかりません");
        }

        // リーダーか確認する
        String teamId = assignmentDoc.getString("teamId");

        if (teamId == null || !isLeader(teamId, loginUid)) {
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

        if (!assignmentDoc.exists()) {
            throw new IllegalArgumentException("課題が見つかりません");
        }

        // リーダーか確認する
        String teamId = assignmentDoc.getString("teamId");

        if (teamId == null || !isLeader(teamId, loginUid)) {
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

        if (!assignmentDoc.exists()) {
            throw new IllegalArgumentException("課題が見つかりません");
        }

        // リーダーか確認する
        String teamId = assignmentDoc.getString("teamId");

        if (teamId == null || !isLeader(teamId, loginUid)) {
            throw new IllegalArgumentException("表示する権限がありません");
        }

        // チェックリストを取得する
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
}
