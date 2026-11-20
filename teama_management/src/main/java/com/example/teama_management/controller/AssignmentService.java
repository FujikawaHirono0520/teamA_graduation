package com.example.teama_management.service;

import com.example.teama_management.form.AssignmentCreateForm;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.firebase.cloud.FirestoreClient;
import com.google.firebase.cloud.StorageClient;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.ZoneId;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AssignmentService {

    public List<String> findLeaderTeams(String loginUid) {
        // 必要に応じて Firestore からリーダー権限のチーム一覧を取得する実装に差し替え
        return Collections.emptyList();
    }

    public void createAssignment(AssignmentCreateForm form, String creatorUid) throws Exception {
        String attachmentUrl = null;
        MultipartFile file = form.getAttachmentFile();

        // opt [添付資料がある場合] 11. 添付資料を保存する
        if (file != null && !file.isEmpty()) {
            Storage storage = StorageClient.getInstance().bucket().getStorage();
            String bucketName = StorageClient.getInstance().bucket().getName();
            String objectName = "assignments/" + UUID.randomUUID() + "_" + file.getOriginalFilename();

            BlobId blobId = BlobId.of(bucketName, objectName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId)
                    .setContentType(file.getContentType())
                    .build();
            storage.create(blobInfo, file.getBytes());
            attachmentUrl = "gs://" + bucketName + "/" + objectName;
        }

        // 10. ステータスを「未着手」に設定し、13. Firestore に登録する
        Firestore db = FirestoreClient.getInstance();
        Map<String, Object> assignmentData = new HashMap<>();
        assignmentData.put("title", form.getTitle().trim());
        assignmentData.put("targetType", form.getTargetType());
        assignmentData.put("teamId", form.getTeamId());
        assignmentData.put("assigneeIds", form.getAssigneeIds());
        assignmentData.put("dueDate", form.getDueDate() != null
                ? Timestamp.of(java.util.Date.from(
                        form.getDueDate().atZone(ZoneId.systemDefault()).toInstant()))
                : null);
        assignmentData.put("priority", form.getPriority());
        assignmentData.put("description", form.getDescription() != null ? form.getDescription().trim() : "");
        assignmentData.put("attachmentUrl", attachmentUrl);
        assignmentData.put("status", "未着手");
        assignmentData.put("createdBy", creatorUid);
        assignmentData.put("createdAt", Timestamp.now());

        db.collection("assignments").document().set(assignmentData).get();
    }
}