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
import org.springframework.web.bind.annotation.PathVariable;
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
// 2026年11月24日追加
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
    // チーム参加画面を表示する
    @GetMapping("/team_join_page")
    public String showTeamJoin(HttpSession session) {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        return "team_join_page";
    }

    // 招待コードでチームに参加する
    @PostMapping("/team_join_page")
    public String joinTeam(
            @RequestParam("inviteCode") String inviteCode,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // 招待コードの入力を確認する
        if (inviteCode == null || inviteCode.isBlank()) {
            model.addAttribute("errorMessage", "招待コードを入力してください");
            return "team_join_page";
        }

        // 前後の空白を削除し、大文字に統一する
        inviteCode = inviteCode.trim().toUpperCase();

        // 招待コードからチームを検索する
        QuerySnapshot teamResult = firestore.collection("teams")
                .whereEqualTo("inviteCode", inviteCode)
                .limit(1)
                .get()
                .get();

        // 未入力の場合
        if (inviteCode.isBlank()) {
            model.addAttribute("errorMessage", "招待コードを入力してください");
            return "team_join_page";
        }

        // 該当するチームが存在しない場合
        if (teamResult.isEmpty()) {
            model.addAttribute("errorMessage", "招待コードが正しくありません");
            model.addAttribute("inviteCode", inviteCode);
            return "team_join_page";
        }

        // 該当するチーム情報を取得する
        DocumentSnapshot teamDoc = teamResult.getDocuments().get(0);

        String teamId = teamDoc.getId();
        String teamName = teamDoc.getString("teamName");

        // すでに参加しているチームか確認する
        QuerySnapshot memberResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", uid)
                .limit(1)
                .get()
                .get();

        // すでに参加している場合
        if (!memberResult.isEmpty()) {
            model.addAttribute(
                    "errorMessage",
                    "すでに参加済みのチームです");

            model.addAttribute("inviteCode", inviteCode);

            return "team_join_page";
        }

        // チーム所属情報を登録する
        DocumentReference memberRef =
                firestore.collection("teammembers").document();

        Map<String, Object> memberData = new HashMap<>();
        memberData.put("teamMemberId", memberRef.getId());
        memberData.put("teamId", teamId);
        memberData.put("userId", uid);
        memberData.put("role", "user");
        memberData.put("joinedAt", FieldValue.serverTimestamp());

        memberRef.set(memberData).get();

        // 参加成功メッセージを設定する
        redirectAttributes.addFlashAttribute(
                "successMessage",
                teamName + "に参加しました");

        // 参加したチームの課題一覧に遷移する
        return "redirect:/assignments?teamId=" + teamId;
    }

    // チーム詳細画面を表示する
    @GetMapping("/team_detail_page/{teamId}")
    public String showTeamDetail(
            @org.springframework.web.bind.annotation.PathVariable("teamId") String teamId,
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // チーム情報を取得する
        DocumentSnapshot teamDoc = firestore.collection("teams")
                .document(teamId)
                .get()
                .get();

        // チーム情報が存在しない場合
        if (!teamDoc.exists()) {
            return "redirect:/settings";
        }

        // チームのメンバー情報を取得する
        QuerySnapshot memberResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .get()
                .get();

        // メンバー一覧
        List<Map<String, String>> members = new ArrayList<>();

        // ログインユーザーの役割
        String userRole = null;

        // メンバー情報を取得する
        for (DocumentSnapshot memberDoc : memberResult.getDocuments()) {

            String memberId = memberDoc.getString("userId");
            String role = memberDoc.getString("role");

            if (memberId == null) {
                continue;
            }

            // ログインユーザーの役割を取得する
            if (memberId.equals(uid)) {
                userRole = role;
            }

            // ユーザー情報を取得する
            DocumentSnapshot userDoc = firestore.collection("users")
                    .document(memberId)
                    .get()
                    .get();

            if (userDoc.exists()) {

                Map<String, String> member = new HashMap<>();

                member.put("id", memberId);
                member.put("name", userDoc.getString("username"));
                member.put("role", role);

                members.add(member);
            }
        }

        // チームに所属していない場合
        if (userRole == null) {
            return "redirect:/settings";
        }

        // チーム情報を画面に渡す
        model.addAttribute("teamId", teamId);
        model.addAttribute("teamName", teamDoc.getString("teamName"));
        model.addAttribute("inviteCode", teamDoc.getString("inviteCode"));

        // メンバー情報を画面に渡す
        model.addAttribute("members", members);
        model.addAttribute("memberCount", members.size());
        model.addAttribute("userRole", userRole);

        return "team_detail_page";
    }

    // チーム編集画面を表示する
    @GetMapping("/team_edit_page/{teamId}")
    public String showTeamEdit(
            @PathVariable("teamId") String teamId,
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // チーム情報を取得する
        DocumentSnapshot teamDoc = firestore.collection("teams")
                .document(teamId)
                .get()
                .get();

        if (!teamDoc.exists()) {
            return "redirect:/settings";
        }

        // リーダーか確認する
        QuerySnapshot result = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", uid)
                .whereEqualTo("role", "leader")
                .get()
                .get();

        if (result.isEmpty()) {
            return "redirect:/settings";
        }

        // チームメンバー情報を取得する
        QuerySnapshot memberResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("role", "user")
                .get()
                .get();

        // 現在のユーザー一覧
        List<Map<String, String>> members = new ArrayList<>();

        for (DocumentSnapshot memberDoc : memberResult.getDocuments()) {

            String memberId = memberDoc.getString("userId");

            if (memberId == null) {
                continue;
            }

            // ユーザー情報を取得する
            DocumentSnapshot userDoc = firestore.collection("users")
                    .document(memberId)
                    .get()
                    .get();

            if (userDoc.exists()) {
                Map<String, String> member = new HashMap<>();
                member.put("id", memberId);
                member.put("name", userDoc.getString("username"));
                members.add(member);
            }
        }

        model.addAttribute("teamId", teamId);
        model.addAttribute("teamName", teamDoc.getString("teamName"));
        model.addAttribute("members", members);

        return "team_edit_page";
    }

    // ユーザー操作画面を表示する
    @GetMapping("/User_action_dialog/{teamId}/{userId}")
    public String showUserAction(
            @PathVariable("teamId") String teamId,
            @PathVariable("userId") String userId,
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // リーダーか確認する
        QuerySnapshot leaderResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", uid)
                .whereEqualTo("role", "leader")
                .get()
                .get();

        // リーダーではない場合
        if (leaderResult.isEmpty()) {
            return "redirect:/settings";
        }

        // 操作対象のユーザーがチームに所属しているか確認する
        QuerySnapshot memberResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", userId)
                .get()
                .get();

        if (memberResult.isEmpty()) {
            return "redirect:/team_detail_page/" + teamId;
        }

        // ユーザー情報を取得する
        DocumentSnapshot userDoc = firestore.collection("users")
                .document(userId)
                .get()
                .get();

        if (!userDoc.exists()) {
            return "redirect:/team_detail_page/" + teamId;
        }

        model.addAttribute("teamId", teamId);
        model.addAttribute("userId", userId);
        model.addAttribute("username", userDoc.getString("username"));

        return "User_action_dialog";
    }

        // チームメンバーを削除する
    @PostMapping("/team_member_delete")
    public String deleteMember(
            @RequestParam("teamId") String teamId,
            @RequestParam("userId") String userId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // ログイン中のユーザーIDを取得する
            String uid = (String) session.getAttribute("loginUid");

            // ログインしていない場合
            if (uid == null) {
                return "redirect:/login";
            }

            // リーダーか確認する
            QuerySnapshot leaderResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("role", "leader")
                    .get()
                    .get();

            if (leaderResult.isEmpty()) {
                return "redirect:/settings";
            }

            // 削除対象のユーザー情報を取得する
            QuerySnapshot memberResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("role", "user")
                    .get()
                    .get();

            // 削除対象が存在しない場合
            if (memberResult.isEmpty() || uid.equals(userId)) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "ユーザー情報の変更に失敗しました");

                return "redirect:/team_detail_page/" + teamId;
            }

            // ユーザーをチームから削除する
            WriteBatch batch = firestore.batch();

            for (DocumentSnapshot doc : memberResult.getDocuments()) {
                batch.delete(doc.getReference());
            }

            batch.commit().get();

            // 削除成功メッセージを設定する
            redirectAttributes.addFlashAttribute(
                "successMessage",
                getUserName(userId) + "をチームから外しました");

            return "redirect:/team_detail_page/" + teamId;

        } catch (Exception e) {
            // エラー内容をコンソールに表示する
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー情報の変更に失敗しました");

            return "redirect:/team_detail_page/" + teamId;
        }
    }

    // リーダーを変更する
    @PostMapping("/team_leader_change")
    public String changeLeader(
            @RequestParam("teamId") String teamId,
            @RequestParam("userId") String userId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // ログイン中のユーザーIDを取得する
            String uid = (String) session.getAttribute("loginUid");

            // ログインしていない場合
            if (uid == null) {
                return "redirect:/login";
            }

            // 現在のリーダーか確認する
            QuerySnapshot leaderResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("role", "leader")
                    .get()
                    .get();

            if (leaderResult.isEmpty()) {
                return "redirect:/settings";
            }

            // 変更対象のユーザー情報を取得する
            QuerySnapshot memberResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", userId)
                    .whereEqualTo("role", "user")
                    .get()
                    .get();

            // 変更対象が存在しない場合
            if (memberResult.isEmpty() || uid.equals(userId)) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "ユーザー情報の変更に失敗しました");

                return "redirect:/team_detail_page/" + teamId;
            }

            // Firestoreのバッチ処理を開始する
            WriteBatch batch = firestore.batch();

            // 現在のリーダーを一般ユーザーに変更する
            for (DocumentSnapshot doc : leaderResult.getDocuments()) {
                batch.update(doc.getReference(), "role", "user");
            }

            // 新しいリーダーに変更する
            for (DocumentSnapshot doc : memberResult.getDocuments()) {
                batch.update(doc.getReference(), "role", "leader");
            }

            // まとめてFirestoreに保存する
            batch.commit().get();

            // リーダー変更成功メッセージを設定する
            redirectAttributes.addFlashAttribute(
                "successMessage",
                getUserName(userId) + "をリーダーにしました");

            return "redirect:/team_detail_page/" + teamId;

        } catch (Exception e) {
            // エラー内容をコンソールに表示する
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー情報の変更に失敗しました");

            return "redirect:/team_detail_page/" + teamId;
        }
    }

    // チーム削除確認画面を表示する
    @GetMapping("/Team_delete_dialog/{teamId}")
    public String showTeamDeleteDialog(
            @PathVariable("teamId") String teamId,
            HttpSession session,
            Model model) throws Exception {

        // ログイン中のユーザーIDを取得する
        String uid = (String) session.getAttribute("loginUid");

        // ログインしていない場合
        if (uid == null) {
            return "redirect:/login";
        }

        // チーム情報を取得する
        DocumentSnapshot teamDoc = firestore.collection("teams")
                .document(teamId)
                .get()
                .get();

        // チーム情報が存在しない場合
        if (!teamDoc.exists()) {
            return "redirect:/settings";
        }

        // リーダーか確認する
        QuerySnapshot leaderResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", uid)
                .whereEqualTo("role", "leader")
                .get()
                .get();

        // リーダーではない場合
        if (leaderResult.isEmpty()) {
            return "redirect:/settings";
        }

        model.addAttribute("teamId", teamId);
        model.addAttribute("teamName", teamDoc.getString("teamName"));

        return "Team_delete_dialog";
    }
    
    // チームを削除する
    @PostMapping("/team_delete")
    public String deleteTeam(
            @RequestParam("teamId") String teamId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // ログイン中のユーザーIDを取得する
            String uid = (String) session.getAttribute("loginUid");

            // ログインしていない場合
            if (uid == null) {
                return "redirect:/login";
            }

            // リーダーか確認する
            QuerySnapshot leaderResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("role", "leader")
                    .get()
                    .get();

            if (leaderResult.isEmpty()) {
                return "redirect:/settings";
            }

            // チーム情報を取得する
            DocumentReference teamRef = firestore.collection("teams")
                    .document(teamId);

            DocumentSnapshot teamDoc = teamRef.get().get();

            if (!teamDoc.exists()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "チームの削除に失敗しました");

                return "redirect:/settings";
            }

            // チームに所属するメンバー情報を取得する
            QuerySnapshot memberResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .get()
                    .get();

            // Firestoreのバッチ処理を開始する
            WriteBatch batch = firestore.batch();

            // メンバー情報を削除する
            for (DocumentSnapshot doc : memberResult.getDocuments()) {
                batch.delete(doc.getReference());
            }

            // チーム情報を削除する
            batch.delete(teamRef);

            // まとめてFirestoreから削除する
            batch.commit().get();

            return "redirect:/settings";

        } catch (Exception e) {
            // エラー内容をコンソールに表示する
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "チームの削除に失敗しました");

            return "redirect:/team_detail_page/" + teamId;
        }
    }

    // チーム情報を更新する
    @PostMapping("/team_edit_page")
    public String updateTeam(
            @RequestParam("teamId") String teamId,
            @RequestParam("teamName") String teamName,
            @RequestParam(value = "memberIds", required = false)
            List<String> memberIds,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            // ログイン中のユーザーIDを取得する
            String uid = (String) session.getAttribute("loginUid");

            // ログインしていない場合
            if (uid == null) {
                return "redirect:/login";
            }

            // リーダーか確認する
            QuerySnapshot leaderResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("role", "leader")
                    .get()
                    .get();

            if (leaderResult.isEmpty()) {
                return "redirect:/settings";
            }

            // チーム名の入力を確認する
            if (teamName == null || teamName.isBlank()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "チーム名を入力してください");

                return "redirect:/team_edit_page/" + teamId;
            }

            teamName = teamName.trim();

            // 追加するユーザーIDの重複を取り除く
            Set<String> uniqueMemberIds = new HashSet<>();

            if (memberIds != null) {
                uniqueMemberIds.addAll(memberIds);
            }

            // 自分自身は追加ユーザーから除外する
            uniqueMemberIds.remove(uid);

            // 選択したユーザーが存在するか確認する
            for (String memberId : uniqueMemberIds) {

                DocumentSnapshot userDoc = firestore.collection("users")
                        .document(memberId)
                        .get()
                        .get();

                if (!userDoc.exists()) {
                    throw new Exception("ユーザーが存在しません");
                }
            }

            // チーム情報を取得する
            DocumentReference teamRef = firestore.collection("teams")
                    .document(teamId);

            DocumentSnapshot teamDoc = teamRef.get().get();

            if (!teamDoc.exists()) {
                throw new Exception("チームが存在しません");
            }

            // 現在のメンバー情報を取得する
            QuerySnapshot memberResult = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("role", "user")
                    .get()
                    .get();

            // Firestoreのバッチ処理を開始する
            WriteBatch batch = firestore.batch();

            // チーム名を更新する
            batch.update(teamRef, "teamName", teamName);
            batch.update(teamRef, "updatedAt", FieldValue.serverTimestamp());

            // 現在のメンバー情報を削除する
            for (DocumentSnapshot doc : memberResult.getDocuments()) {
                batch.delete(doc.getReference());
            }

            // 選択したユーザーを登録する
            for (String memberId : uniqueMemberIds) {
                addTeamMember(batch, teamId, memberId, "user");
            }

            // まとめてFirestoreに保存する
            batch.commit().get();

            // 更新成功メッセージを設定する
            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    "チーム情報を更新しました");

            return "redirect:/team_detail_page/" + teamId;

        } catch (Exception e) {

            // エラー内容をコンソールに表示する
            e.printStackTrace();

            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "チーム情報の更新に失敗しました");

            return "redirect:/team_detail_page/" + teamId;
        }
    }
}
