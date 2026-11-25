package com.example.teama_management.controller;

import com.example.teama_management.form.AssignmentCreateForm;

import com.google.cloud.Timestamp;

import com.google.cloud.firestore.DocumentSnapshot;

import com.google.cloud.firestore.Firestore;

import com.google.cloud.firestore.QuerySnapshot;

import com.google.cloud.storage.BlobId;

import com.google.cloud.storage.BlobInfo;

import com.google.cloud.storage.Bucket;

import com.google.cloud.storage.Storage;

import com.google.firebase.FirebaseApp;

import com.google.firebase.cloud.StorageClient;
import org.springframework.stereotype.Service;

import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDateTime;

import java.time.ZoneId;

import java.util.ArrayList;

import java.util.Date;

import java.util.HashMap;

import java.util.HashSet;

import java.util.List;

import java.util.Map;

import java.util.Set;

import java.util.UUID;

@Service

public class AssignmentService {

    private final Firestore firestore;

    public AssignmentService(Firestore firestore) {

        this.firestore = firestore;

    }

    // ログインユーザーがリーダーのチーム一覧を取得する

    public List<Map<String, String>> findLeaderTeams(String loginUid)

            throws Exception {

        List<Map<String, String>> teams = new ArrayList<>();

        QuerySnapshot result = firestore.collection("teammembers")

                .whereEqualTo("userId", loginUid)

                .whereEqualTo("role", "leader")

                .get()

                .get();

        for (DocumentSnapshot memberDoc : result.getDocuments()) {

            String teamId = memberDoc.getString("teamId");

            if (teamId == null) {

                continue;

            }

            DocumentSnapshot teamDoc = firestore.collection("teams")

                    .document(teamId)

                    .get()

                    .get();

            if (teamDoc.exists()) {

                Map<String, String> team = new HashMap<>();

                team.put("id", teamId);

                team.put("name", teamDoc.getString("teamName"));

                teams.add(team);

            }

        }

        return teams;

    }

    // リーダーが所属するチームのメンバーを名前で検索する

    public List<Map<String, String>> searchLeaderTeamMembers(

            String loginUid, String keyword) throws Exception {

        List<Map<String, String>> users = new ArrayList<>();

        Set<String> memberIds = new HashSet<>();

        if (keyword == null || keyword.isBlank()) {

            return users;

        }

        keyword = keyword.trim();

        // 自分がリーダーのチームを取得する

        QuerySnapshot leaderTeams = firestore.collection("teammembers")

                .whereEqualTo("userId", loginUid)

                .whereEqualTo("role", "leader")

                .get()

                .get();

        for (DocumentSnapshot leaderDoc : leaderTeams.getDocuments()) {

            String teamId = leaderDoc.getString("teamId");

            if (teamId == null) {

                continue;

            }

            // チームの一般メンバーを取得する

            QuerySnapshot members = firestore.collection("teammembers")

                    .whereEqualTo("teamId", teamId)

                    .whereEqualTo("role", "user")

                    .get()

                    .get();

            for (DocumentSnapshot memberDoc : members.getDocuments()) {

                String userId = memberDoc.getString("userId");

                if (userId == null || userId.equals(loginUid)

                        || memberIds.contains(userId)) {

                    continue;

                }

                memberIds.add(userId);

                // ユーザー情報を取得する

                DocumentSnapshot userDoc = firestore.collection("users")

                        .document(userId)

                        .get()

                        .get();

                if (!userDoc.exists()) {

                    continue;

                }

                String username = userDoc.getString("username");

                if (username != null && username.startsWith(keyword)) {

                    Map<String, String> user = new HashMap<>();

                    user.put("id", userId);

                    user.put("name", username);

                    users.add(user);

                }

            }

        }

        return users;

    }

    // 課題を登録する

    public void createAssignment(

            AssignmentCreateForm form, String creatorUid) throws Exception {

        List<String> assigneeIds = new ArrayList<>();

        String resolvedTeamId = null;

        boolean isTeamTarget = "team".equals(form.getTargetType());

        boolean isIndividualTarget = "individual".equals(form.getTargetType());

        if (isTeamTarget) {

            resolvedTeamId = form.getTeamId();

            // 選択したチームのリーダーか確認する

            QuerySnapshot leaderResult = firestore.collection("teammembers")

                    .whereEqualTo("teamId", resolvedTeamId)

                    .whereEqualTo("userId", creatorUid)

                    .whereEqualTo("role", "leader")

                    .get()

                    .get();

            if (leaderResult.isEmpty()) {

                throw new IllegalArgumentException("選択したチームを確認できません");

            }

            // チームに所属するメンバー全員を取得する

            QuerySnapshot memberResult = firestore.collection("teammembers")

                    .whereEqualTo("teamId", resolvedTeamId)

                    .get()

                    .get();

            for (DocumentSnapshot memberDoc : memberResult.getDocuments()) {

                String userId = memberDoc.getString("userId");

                if (userId != null && !assigneeIds.contains(userId)) {

                    assigneeIds.add(userId);

                }

            }

            if (assigneeIds.isEmpty()) {

                throw new IllegalArgumentException("チームメンバーが存在しません");

            }

        } else if (isIndividualTarget) {

            if (form.getAssigneeIds() == null

                    || form.getAssigneeIds().isEmpty()) {

                throw new IllegalArgumentException("担当者を選択してください");

            }

            // 自分がリーダーのチームに所属するメンバーIDと、そのteamIdを紐づける

            Set<String> leaderMemberIds = new HashSet<>();

            Map<String, String> memberToTeamId = new HashMap<>();

            QuerySnapshot leaderTeams = firestore.collection("teammembers")

                    .whereEqualTo("userId", creatorUid)

                    .whereEqualTo("role", "leader")

                    .get()

                    .get();

            for (DocumentSnapshot leaderDoc : leaderTeams.getDocuments()) {

                String teamId = leaderDoc.getString("teamId");

                if (teamId == null) {

                    continue;

                }

                QuerySnapshot members = firestore.collection("teammembers")

                        .whereEqualTo("teamId", teamId)

                        .whereEqualTo("role", "user")

                        .get()

                        .get();

                for (DocumentSnapshot memberDoc : members.getDocuments()) {

                    String userId = memberDoc.getString("userId");

                    if (userId != null) {

                        leaderMemberIds.add(userId);

                        memberToTeamId.putIfAbsent(userId, teamId);

                    }

                }

            }

            // 選択したユーザーが対象チームのメンバーか確認し、teamIdを特定する

            for (String userId : form.getAssigneeIds()) {

                if (userId == null || !leaderMemberIds.contains(userId)) {

                    throw new IllegalArgumentException("選択した担当者を確認できません");

                }

                if (resolvedTeamId == null) {

                    resolvedTeamId = memberToTeamId.get(userId);

                }

                if (!assigneeIds.contains(userId)) {

                    assigneeIds.add(userId);

                }

            }

        } else {

            throw new IllegalArgumentException("課題の対象が正しくありません");

        }

        String attachmentUrl = null;

        MultipartFile file = form.getAttachmentFile();

        // 添付資料がある場合は保存する

        if (file != null && !file.isEmpty()) {

            FirebaseApp app = FirebaseApp.getInstance();

            Bucket bucket = StorageClient.getInstance(app).bucket();

            Storage storage = bucket.getStorage();

            String bucketName = bucket.getName();

            String objectName = "assignments/"

                    + UUID.randomUUID() + "_" + file.getOriginalFilename();

            BlobId blobId = BlobId.of(bucketName, objectName);

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)

                    .setContentType(file.getContentType())

                    .build();

            storage.create(blobInfo, file.getBytes());

            attachmentUrl = "gs://" + bucketName + "/" + objectName;

        }

        // 課題情報を作成する

        Map<String, Object> assignmentData = new HashMap<>();

        assignmentData.put("title", form.getTitle().trim());

        assignmentData.put("targetType", form.getTargetType());

        assignmentData.put("teamId", resolvedTeamId);

        assignmentData.put("assigneeIds", assigneeIds);

        assignmentData.put("dueDate", form.getDueDate() != null

                ? Timestamp.of(Date.from(

                        form.getDueDate()

                                .atZone(ZoneId.systemDefault())

                                .toInstant()))

                : null);

        assignmentData.put("priority", form.getPriority());

        assignmentData.put("description",

                form.getDescription() != null

                        ? form.getDescription().trim()
                        : "");

        assignmentData.put("attachmentUrl", attachmentUrl);

        assignmentData.put("status", "未着手");

        assignmentData.put("createdBy", creatorUid);

        assignmentData.put("createdAt", Timestamp.now());

        firestore.collection("assignments")

                .document()

                .set(assignmentData)

                .get();

    }

    // 編集する課題情報を取得する

    public AssignmentCreateForm findAssignmentForEdit(

            String assignmentId, String loginUid) throws Exception {

        DocumentSnapshot assignmentDoc = firestore.collection("assignments")

                .document(assignmentId)

                .get()

                .get();

        if (!assignmentDoc.exists()) {

            return null;

        }

        // 課題のチームIDを取得する

        String teamId = assignmentDoc.getString("teamId");

        if (teamId == null) {

            return null;

        }

        // ログインユーザーが対象チームのリーダーか確認する

        QuerySnapshot leaderResult = firestore.collection("teammembers")

                .whereEqualTo("teamId", teamId)

                .whereEqualTo("userId", loginUid)

                .whereEqualTo("role", "leader")

                .get()

                .get();

        if (leaderResult.isEmpty()) {

            return null;

        }

        AssignmentCreateForm form = new AssignmentCreateForm();

        form.setTitle(assignmentDoc.getString("title"));

        form.setTargetType(assignmentDoc.getString("targetType"));

        form.setTeamId(teamId);

        // 担当者IDを取得する

        List<String> assigneeIds = new ArrayList<>();

        Object assigneeData = assignmentDoc.get("assigneeIds");

        if (assigneeData instanceof List<?>) {

            for (Object userId : (List<?>) assigneeData) {

                if (userId instanceof String) {

                    assigneeIds.add((String) userId);

                }

            }

        }

        form.setAssigneeIds(assigneeIds);

        // 締め切り日時を取得する

        Timestamp dueDate = assignmentDoc.getTimestamp("dueDate");

        if (dueDate != null) {

            LocalDateTime dateTime = dueDate.toDate()

                    .toInstant()

                    .atZone(ZoneId.systemDefault())

                    .toLocalDateTime();

            form.setDueDate(dateTime);

        }

        form.setPriority(assignmentDoc.getString("priority"));

        form.setDescription(assignmentDoc.getString("description"));

        return form;

    }

    // 担当者IDからユーザー情報を取得する

    public List<Map<String, String>> findAssigneeUsers(

            List<String> assigneeIds) throws Exception {

        List<Map<String, String>> users = new ArrayList<>();

        if (assigneeIds == null) {

            return users;

        }

        for (String userId : assigneeIds) {

            if (userId == null) {

                continue;

            }

            // ユーザー情報を取得する

            DocumentSnapshot userDoc = firestore.collection("users")

                    .document(userId)

                    .get()

                    .get();

            if (userDoc.exists()) {

                Map<String, String> user = new HashMap<>();

                user.put("id", userId);

                user.put("name", userDoc.getString("username"));

                users.add(user);

            }

        }

        return users;

    }

    // 課題情報を更新する

    public void updateAssignment(

            String assignmentId,

            AssignmentCreateForm form,

            String loginUid) throws Exception {

        DocumentSnapshot assignmentDoc = firestore.collection("assignments")

                .document(assignmentId)

                .get()

                .get();

        if (!assignmentDoc.exists()) {

            throw new IllegalArgumentException("課題が見つかりません");

        }

        // 課題のチームIDを取得する

        String teamId = assignmentDoc.getString("teamId");

        if (teamId == null) {

            throw new IllegalArgumentException("課題のチームを確認できません");

        }

        // ログインユーザーが対象チームのリーダーか確認する

        QuerySnapshot leaderResult = firestore.collection("teammembers")

                .whereEqualTo("teamId", teamId)

                .whereEqualTo("userId", loginUid)

                .whereEqualTo("role", "leader")

                .get()

                .get();

        if (leaderResult.isEmpty()) {

            throw new IllegalArgumentException("課題を編集する権限がありません");

        }

        List<String> assigneeIds = new ArrayList<>();

        String resolvedTeamId = null;

        boolean isTeamTarget = "team".equals(form.getTargetType());

        boolean isIndividualTarget = "individual".equals(form.getTargetType());

        if (isTeamTarget) {

            resolvedTeamId = form.getTeamId();

            // 選択したチームのリーダーか確認する

            QuerySnapshot selectedLeaderResult = firestore.collection("teammembers")

                    .whereEqualTo("teamId", resolvedTeamId)

                    .whereEqualTo("userId", loginUid)

                    .whereEqualTo("role", "leader")

                    .get()

                    .get();

            if (selectedLeaderResult.isEmpty()) {

                throw new IllegalArgumentException("選択したチームを確認できません");

            }

            // チームに所属するメンバー全員を取得する

            QuerySnapshot memberResult = firestore.collection("teammembers")

                    .whereEqualTo("teamId", resolvedTeamId)

                    .get()

                    .get();

            for (DocumentSnapshot memberDoc : memberResult.getDocuments()) {

                String userId = memberDoc.getString("userId");

                if (userId != null && !assigneeIds.contains(userId)) {

                    assigneeIds.add(userId);

                }

            }

            if (assigneeIds.isEmpty()) {

                throw new IllegalArgumentException("チームメンバーが存在しません");

            }

        } else if (isIndividualTarget) {

            if (form.getAssigneeIds() == null

                    || form.getAssigneeIds().isEmpty()) {

                throw new IllegalArgumentException("担当者を選択してください");

            }

            // 自分がリーダーのチームに所属するメンバーIDと、そのteamIdを紐づける

            Set<String> leaderMemberIds = new HashSet<>();

            Map<String, String> memberToTeamId = new HashMap<>();

            QuerySnapshot leaderTeams = firestore.collection("teammembers")

                    .whereEqualTo("userId", loginUid)

                    .whereEqualTo("role", "leader")

                    .get()

                    .get();

            for (DocumentSnapshot leaderDoc : leaderTeams.getDocuments()) {

                String leaderTeamId = leaderDoc.getString("teamId");

                if (leaderTeamId == null) {

                    continue;

                }

                QuerySnapshot members = firestore.collection("teammembers")

                        .whereEqualTo("teamId", leaderTeamId)

                        .whereEqualTo("role", "user")

                        .get()

                        .get();

                for (DocumentSnapshot memberDoc : members.getDocuments()) {

                    String userId = memberDoc.getString("userId");

                    if (userId != null) {

                        leaderMemberIds.add(userId);

                        memberToTeamId.putIfAbsent(userId, leaderTeamId);

                    }

                }

            }

            // 選択したユーザーが対象チームのメンバーか確認し、teamIdを特定する

            for (String userId : form.getAssigneeIds()) {

                if (userId == null || !leaderMemberIds.contains(userId)) {

                    throw new IllegalArgumentException("選択した担当者を確認できません");

                }

                if (resolvedTeamId == null) {

                    resolvedTeamId = memberToTeamId.get(userId);

                }

                if (!assigneeIds.contains(userId)) {

                    assigneeIds.add(userId);

                }

            }

        } else {

            throw new IllegalArgumentException("課題の対象が正しくありません");

        }

        // 既存の添付資料URLを取得する

        String attachmentUrl = assignmentDoc.getString("attachmentUrl");

        MultipartFile file = form.getAttachmentFile();

        // 新しい添付資料がある場合は保存する

        if (file != null && !file.isEmpty()) {

            FirebaseApp app = FirebaseApp.getInstance();

            Bucket bucket = StorageClient.getInstance(app).bucket();

            Storage storage = bucket.getStorage();

            String bucketName = bucket.getName();

            String objectName = "assignments/"

                    + UUID.randomUUID() + "_" + file.getOriginalFilename();

            BlobId blobId = BlobId.of(bucketName, objectName);

            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)

                    .setContentType(file.getContentType())

                    .build();

            storage.create(blobInfo, file.getBytes());

            attachmentUrl = "gs://" + bucketName + "/" + objectName;

        }

        // 課題情報を更新する

        Map<String, Object> assignmentData = new HashMap<>();

        assignmentData.put("title", form.getTitle().trim());

        assignmentData.put("targetType", form.getTargetType());

        assignmentData.put("teamId", resolvedTeamId);

        assignmentData.put("assigneeIds", assigneeIds);

        assignmentData.put("dueDate", form.getDueDate() != null

                ? Timestamp.of(Date.from(

                        form.getDueDate()

                                .atZone(ZoneId.systemDefault())

                                .toInstant()))

                : null);

        assignmentData.put("priority", form.getPriority());

        assignmentData.put("description",

                form.getDescription() != null

                        ? form.getDescription().trim()
                        : "");

        assignmentData.put("attachmentUrl", attachmentUrl);

        firestore.collection("assignments")

                .document(assignmentId)

                .update(assignmentData)

                .get();

    }

}
