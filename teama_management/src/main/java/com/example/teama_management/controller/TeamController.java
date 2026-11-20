package com.example.teama_management.controller;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Controller
public class TeamController {

    private final Firestore firestore;

    public TeamController(Firestore firestore) {
        this.firestore = firestore;
    }

    // チーム作成画面を表示する
    @GetMapping("/team_create_page")
    public String showTeamCreate(
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // ログイン中のユーザー情報を取得する
        DocumentSnapshot userDoc = firestore.collection("users")
                .document(uid)
                .get()
                .get();

        if (!userDoc.exists()) {
            return "redirect:/login";
        }

        model.addAttribute("leaderName", userDoc.getString("username"));

        return "team_create_page";
    }

    // ユーザーを検索する
    @GetMapping("/team_create_page/search")
    @ResponseBody
    public List<Map<String, String>> searchUsers(
            @RequestParam("keyword") String keyword,
            HttpSession session) throws Exception {

        List<Map<String, String>> users = new ArrayList<>();

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        if (uid == null || keyword == null || keyword.isBlank()) {
            return users;
        }

        keyword = keyword.trim();

        // ユーザーIDで検索する
        DocumentSnapshot userDoc = firestore.collection("users")
                .document(keyword)
                .get()
                .get();

        if (userDoc.exists() && !keyword.equals(uid)) {
            Map<String, String> user = new HashMap<>();
            user.put("id", userDoc.getId());
            user.put("name", userDoc.getString("username"));
            users.add(user);
        }

        // ユーザー名で前方一致検索する
        QuerySnapshot result = firestore.collection("users")
                .whereGreaterThanOrEqualTo("username", keyword)
                .whereLessThanOrEqualTo("username", keyword + "\uf8ff")
                .limit(10)
                .get()
                .get();

        for (DocumentSnapshot doc : result.getDocuments()) {

            // 自分自身は検索結果に含めない
            if (doc.getId().equals(uid)) {
                continue;
            }

            // 重複を防ぐ
            boolean exists = false;

            for (Map<String, String> user : users) {
                if (user.get("id").equals(doc.getId())) {
                    exists = true;
                    break;
                }
            }

            if (!exists) {
                Map<String, String> user = new HashMap<>();
                user.put("id", doc.getId());
                user.put("name", doc.getString("username"));
                users.add(user);
            }
        }

        return users;
    }

    // チームを作成する
    @PostMapping("/team_create_page")
    public String createTeam(
            @RequestParam("teamName") String teamName,
            @RequestParam(value = "memberIds", required = false)
            List<String> memberIds,
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        if (uid == null) {
            return "redirect:/login";
        }

        // チーム名の入力を確認する
        if (teamName == null || teamName.isBlank()) {

            model.addAttribute(
                "errorMessage",
                "チーム名を入力してください");

            model.addAttribute("teamName", "");

            return showTeamCreate(session, model);
        }

        teamName = teamName.trim();

        try {

            // 追加するユーザーIDの重複を取り除く
            Set<String> uniqueMemberIds = new HashSet<>();

            if (memberIds != null) {
                uniqueMemberIds.addAll(memberIds);
            }

            // 自分自身は追加ユーザーから除外する
            uniqueMemberIds.remove(uid);

            // ユーザーが1人も追加されていない場合
            if (uniqueMemberIds.isEmpty()) {

                model.addAttribute(
                    "errorMessage",
                    "ユーザーを1人以上追加してください");

                model.addAttribute("teamName", teamName);
                model.addAttribute("leaderName", getUserName(uid));

                return "team_create_page";
            }

            // チームIDを自動生成する
            DocumentReference teamRef =
                    firestore.collection("teams").document();

            String teamId = teamRef.getId();

            // 重複しない招待コードを生成する
            String inviteCode = createInviteCode();

            // チーム情報を作成する
            Map<String, Object> teamData = new HashMap<>();
            teamData.put("teamId", teamId);
            teamData.put("teamName", teamName);
            teamData.put("description", "");
            teamData.put("inviteCode", inviteCode);
            teamData.put("createdBy", uid);
            teamData.put("createdAt", FieldValue.serverTimestamp());
            teamData.put("updatedAt", FieldValue.serverTimestamp());

            // Firestoreのバッチ処理を開始する
            WriteBatch batch = firestore.batch();

            // チーム情報を登録する
            batch.set(teamRef, teamData);

            // 作成者をリーダーとして登録する
            addTeamMember(batch, teamId, uid, "leader");

            // 選択したユーザーを登録する
            for (String memberId : uniqueMemberIds) {
                addTeamMember(batch, teamId, memberId, "user");
            }

            // まとめてFirestoreに保存する
            batch.commit().get();

            // 作成後は設定画面に戻る
            return "redirect:/settings";

        } catch (Exception e) {

            // エラー内容をコンソールに表示する
            e.printStackTrace();

            // チーム作成失敗のメッセージを設定する
            model.addAttribute(
                    "errorMessage",
                    "チームの作成に失敗しました");

            model.addAttribute("teamName", teamName);

            try {
                model.addAttribute("leaderName", getUserName(uid));
            } catch (Exception ex) {
                model.addAttribute("leaderName", "");
            }

            return "team_create_page";
        }
    }

    // チーム所属情報を登録する
    private void addTeamMember(
            WriteBatch batch,
            String teamId,
            String userId,
            String role) {

        // 所属情報IDを自動生成する
        DocumentReference memberRef =
                firestore.collection("teammembers").document();

        Map<String, Object> memberData = new HashMap<>();
        memberData.put("teamMemberId", memberRef.getId());
        memberData.put("teamId", teamId);
        memberData.put("userId", userId);
        memberData.put("role", role);
        memberData.put("joinedAt", FieldValue.serverTimestamp());

        batch.set(memberRef, memberData);
    }

    // 招待コードを生成する
    private String createInviteCode() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
    }

    // ユーザー名を取得する
    private String getUserName(String uid) throws Exception {
        DocumentSnapshot userDoc = firestore.collection("users")
                .document(uid)
                .get()
                .get();

        return userDoc.getString("username");
    }
}
