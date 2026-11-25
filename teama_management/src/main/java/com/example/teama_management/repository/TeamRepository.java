package com.example.teama_management.repository;

import com.example.teama_management.model.TeamMembership;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
public class TeamRepository {

    private static final String TEAM_MEMBER_COLLECTION = "teammembers";
    private static final String TEAMS_COLLECTION = "teams";

    private final Firestore firestore;

    public TeamRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    // ログイン中のユーザーが所属しているチームを取得する
    public List<TeamMembership> findMemberships(String uid) throws Exception {
        if (uid == null || uid.isBlank()) {
            return List.of();
        }

        List<QueryDocumentSnapshot> memberDocs = firestore.collection(TEAM_MEMBER_COLLECTION)
                .whereEqualTo("userId", uid)
                .get().get().getDocuments();
        if (memberDocs.isEmpty()) {
            return List.of();
        }

        Map<String, String> teamNameMap = new HashMap<>();
        for (QueryDocumentSnapshot d : memberDocs) {
            String teamId = d.getString("teamId");
            if (teamId != null && !teamNameMap.containsKey(teamId)) {
                DocumentSnapshot teamDoc = firestore.collection(TEAMS_COLLECTION)
                        .document(teamId).get().get();
                String teamName = teamDoc.exists() ? nz(teamDoc.getString("teamName")) : teamId;
                teamNameMap.put(teamId, teamName);
            }
        }

        List<TeamMembership> list = new ArrayList<>();
        for (QueryDocumentSnapshot d : memberDocs) {
            String teamId = nz(d.getString("teamId"));
            String teamName = teamNameMap.getOrDefault(teamId, teamId);
            String role = nz(d.getString("role"));
            list.add(new TeamMembership(teamId, teamName, role));
        }

        list.sort(Comparator.comparing(TeamMembership::teamName,
                Comparator.nullsLast(Comparator.naturalOrder())));
        return list;
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }
}