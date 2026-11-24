package com.example.teama_management.repository;

import com.example.teama_management.model.AssignmentItem;
import com.example.teama_management.model.AssignmentStatus;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class AssignmentRepository {

    private static final String TASKS_COLLECTION = "tasks";
    private static final String TASK_ASSIGNMENTS_COLLECTION = "teamassignments";
    private static final String TASK_PROGRESS_COLLECTION = "teamprogress";
    private static final String USERS_COLLECTION = "users";

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final Firestore firestore;

    public AssignmentRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public List<AssignmentItem> find(String teamId, String uid, boolean leader,
                                     String keyword, String status) throws Exception {
        if (teamId == null || teamId.isBlank()) {
            return List.of();
        }

        List<QueryDocumentSnapshot> taskDocs = firestore.collection(TASKS_COLLECTION)
                .whereEqualTo("teamId", teamId)
                .get().get().getDocuments();
        if (taskDocs.isEmpty()) {
            return List.of();
        }

        Map<String, List<QueryDocumentSnapshot>> assignmentsByTaskId = new HashMap<>();
        Set<String> userIdsToLookup = new HashSet<>();

        for (QueryDocumentSnapshot taskDoc : taskDocs) {
            String taskId = resolveTaskId(taskDoc);
            List<QueryDocumentSnapshot> assignDocs = firestore.collection(TASK_ASSIGNMENTS_COLLECTION)
                    .whereEqualTo("taskId", taskId)
                    .get().get().getDocuments();
            assignmentsByTaskId.put(taskId, assignDocs);

            for (QueryDocumentSnapshot ad : assignDocs) {
                String assigneeType = ad.getString("assigneeType");
                String assigneeId = ad.getString("assigneeId");
                if ("user".equals(assigneeType) && assigneeId != null && !assigneeId.isBlank()) {
                    userIdsToLookup.add(assigneeId);
                }
            }
        }

        Map<String, String> userNameMap = new HashMap<>();
        for (String targetUid : userIdsToLookup) {
            DocumentSnapshot userDoc = firestore.collection(USERS_COLLECTION)
                    .document(targetUid).get().get();
            if (userDoc.exists()) {
                userNameMap.put(targetUid, nz(userDoc.getString("name")));
            }
        }

        String word = keyword == null ? "" : keyword.trim().toLowerCase();
        List<AssignmentItem> items = new ArrayList<>();

        for (QueryDocumentSnapshot d : taskDocs) {
            String taskId = resolveTaskId(d);
            List<QueryDocumentSnapshot> assignDocs = assignmentsByTaskId.getOrDefault(taskId, List.of());

            if (!leader) {
                boolean assignedToMe = assignDocs.stream().anyMatch(ad -> {
                    String type = ad.getString("assigneeType");
                    String targetId = ad.getString("assigneeId");
                    return ("user".equals(type) && uid != null && uid.equals(targetId))
                            || ("team".equals(type) && teamId.equals(targetId));
                });
                if (!assignedToMe) {
                    continue;
                }
            }

            String title = nz(d.getString("title"));
            String assigneeDisplay = buildAssigneeNames(assignDocs, teamId, userNameMap);

            String rawStatus = d.getString("status");
            Timestamp effectiveUpdated = d.getTimestamp("updatedAt");

            if (!leader && uid != null && !uid.isBlank()) {
                QueryDocumentSnapshot myProgress = findUserProgress(taskId, uid);
                if (myProgress != null) {
                    String progStatus = myProgress.getString("status");
                    if (progStatus != null && !progStatus.isBlank()) {
                        rawStatus = progStatus;
                    }
                    Timestamp progUpdated = myProgress.getTimestamp("updatedAt");
                    if (progUpdated != null) {
                        effectiveUpdated = progUpdated;
                    }
                }
            }

            AssignmentStatus st = AssignmentStatus.fromCode(rawStatus);

            boolean match = word.isEmpty()
                    || title.toLowerCase().contains(word)
                    || (leader && assigneeDisplay.toLowerCase().contains(word));
            if (!match) {
                continue;
            }

            if (!"all".equals(status) && !st.code().equals(status)) {
                continue;
            }

            Timestamp deadline = d.getTimestamp("dueDate");
            items.add(new AssignmentItem(
                    taskId,
                    title,
                    st,
                    format(deadline),
                    format(effectiveUpdated),
                    assigneeDisplay,
                    millis(deadline),
                    millis(effectiveUpdated)
            ));
        }

        if (leader) {
            items.sort(Comparator.comparingLong(AssignmentItem::updatedMillis).reversed());
        } else {
            items.sort(Comparator.comparingLong(AssignmentItem::deadlineMillis));
        }
        return items;
    }

    private String resolveTaskId(QueryDocumentSnapshot taskDoc) {
        String fieldTaskId = nz(taskDoc.getString("taskId"));
        return fieldTaskId.isEmpty() ? taskDoc.getId() : fieldTaskId;
    }

    private QueryDocumentSnapshot findUserProgress(String taskId, String uid) throws Exception {
        List<QueryDocumentSnapshot> progressDocs = firestore.collection(TASK_PROGRESS_COLLECTION)
                .whereEqualTo("taskId", taskId)
                .get().get().getDocuments();
        for (QueryDocumentSnapshot doc : progressDocs) {
            if (uid.equals(doc.getString("userId"))) {
                return doc;
            }
        }
        return null;
    }

    private String buildAssigneeNames(List<QueryDocumentSnapshot> assignDocs,
                                      String teamId,
                                      Map<String, String> userNameMap) {
        if (assignDocs.isEmpty()) {
            return "未設定";
        }
        List<String> names = new ArrayList<>();
        for (QueryDocumentSnapshot ad : assignDocs) {
            String type = ad.getString("assigneeType");
            String targetId = ad.getString("assigneeId");
            if ("team".equals(type) && teamId.equals(targetId)) {
                names.add("チーム全員");
            } else if ("user".equals(type) && targetId != null) {
                names.add(userNameMap.getOrDefault(targetId, targetId));
            }
        }
        return names.isEmpty() ? "未設定" : String.join(", ", names);
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    private String format(Timestamp ts) {
        if (ts == null) {
            return "－";
        }
        return LocalDateTime.ofInstant(ts.toDate().toInstant(), ZONE).format(FORMAT);
    }

    private long millis(Timestamp ts) {
        return ts == null ? Long.MAX_VALUE : ts.toDate().getTime();
    }
}