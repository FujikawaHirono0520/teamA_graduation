package com.example.teama_management.repository;

import com.example.teama_management.model.AssignmentItem;
import com.example.teama_management.model.AssignmentStatus;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
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

    private static final ZoneId ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("M/d HH:mm");

    private final Firestore firestore;

    public AssignmentRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // 選択中のチームの課題を取得する
    // ユーザー：自分が担当の課題（チーム全体への課題を含む）／リーダー：チーム全体の課題
    public List<AssignmentItem> find(String teamId, String uid, boolean leader,
                                     String keyword, String status) throws Exception {
        if (teamId == null || teamId.isBlank()) {
            return List.of();
        }

        List<QueryDocumentSnapshot> docs = firestore.collection("assignments")
                .whereEqualTo("teamId", teamId)
                .get().get().getDocuments();
        if (docs.isEmpty()) {
            return List.of();
        }

        // 担当者の名前を、まとめて取得する
        Set<String> allIds = new HashSet<>();
        for (QueryDocumentSnapshot d : docs) {
            allIds.addAll(assigneeIds(d));
        }
        Map<String, String> userNames = loadUserNames(allIds);

        String word = keyword == null ? "" : keyword.trim().toLowerCase();
        List<AssignmentItem> items = new ArrayList<>();

        for (QueryDocumentSnapshot d : docs) {
            List<String> ids = assigneeIds(d);
            boolean teamTarget = "team".equals(nz(d.getString("targetType")));

            // ユーザーは、チーム全体の課題か、自分が担当の課題だけ見える
            if (!leader && !(teamTarget || ids.contains(uid))) {
                continue;
            }

            String title = nz(d.getString("title"));
            String assigneeDisplay = teamTarget ? "チーム全員" : joinNames(ids, userNames);
            AssignmentStatus st = AssignmentStatus.fromCode(d.getString("status"));

            // 検索：ユーザーは課題名、リーダーは課題名または担当者名
            boolean match = word.isEmpty()
                    || title.toLowerCase().contains(word)
                    || (leader && assigneeDisplay.toLowerCase().contains(word));
            if (!match) {
                continue;
            }
            // 絞り込み
            if (!"all".equals(status) && !st.code().equals(status)) {
                continue;
            }

            Timestamp deadline = d.getTimestamp("dueDate");
            Timestamp updated = d.getTimestamp("updatedAt");
            if (updated == null) {
                updated = d.getTimestamp("createdAt");   // 更新日がなければ登録日を使う
            }
            items.add(new AssignmentItem(d.getId(), title, st, format(deadline), format(updated),
                    assigneeDisplay, millis(deadline), millis(updated)));
        }

        // 並び順：ユーザー＝締め切りが近い順／リーダー＝最終更新が新しい順
        if (leader) {
            items.sort(Comparator.comparingLong(AssignmentItem::updatedMillis).reversed());
        } else {
            items.sort(Comparator.comparingLong(AssignmentItem::deadlineMillis));
        }
        return items;
    }

    private List<String> assigneeIds(QueryDocumentSnapshot d) {
        List<String> ids = new ArrayList<>();
        Object value = d.get("assigneeIds");
        if (value instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s) {
                    ids.add(s);
                }
            }
        }
        return ids;
    }

    private Map<String, String> loadUserNames(Set<String> ids) throws Exception {
        Map<String, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        DocumentReference[] refs = ids.stream()
                .map(id -> firestore.collection("users").document(id))
                .toArray(DocumentReference[]::new);
        for (DocumentSnapshot u : firestore.getAll(refs).get()) {
            if (u.exists()) {
                String name = nz(u.getString("username"));
                if (name.isEmpty()) {
                    name = nz(u.getString("name"));
                }
                map.put(u.getId(), name);
            }
        }
        return map;
    }

    private String joinNames(List<String> ids, Map<String, String> userNames) {
        List<String> names = new ArrayList<>();
        for (String id : ids) {
            String name = userNames.getOrDefault(id, "");
            if (!name.isEmpty()) {
                names.add(name);
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