package com.example.teama_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;

import jakarta.servlet.http.HttpSession;

public @interface MemberController {
    @Controller
public class MemberRoleController {

    private final Firestore firestore;

    public MemberRoleController(Firestore firestore) {
        this.firestore = firestore;
    }

    // メンバーをリーダーにする（現在のリーダーは一般ユーザーに戻す）
    @PostMapping("/team/{teamId}/members/promote_leader")
    public String promoteToLeader(
            @PathVariable("teamId") String teamId,
            @RequestParam("targetUserId") String targetUserId,
            @RequestParam("targetUserName") String targetUserName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            String uid = (String) session.getAttribute("loginUid");
            if (uid == null) {
                return "redirect:/login";
            }

            // 現在のリーダーの teambembers ドキュメントを取得
            QuerySnapshot currentLeaderQuery = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", uid)
                    .whereEqualTo("role", "leader")
                    .get()
                    .get();

            if (currentLeaderQuery.isEmpty()) {
                return "redirect:/settings";
            }

            // 昇格対象メンバーの teambembers ドキュメントを取得
            QuerySnapshot targetQuery = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", targetUserId)
                    .get()
                    .get();

            if (targetQuery.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "ユーザー情報の変更に失敗しました");
                return "redirect:/team_detail_page/" + teamId;
            }

            WriteBatch batch = firestore.batch();

            // 対象メンバーを leader に更新
            for (DocumentSnapshot doc : targetQuery.getDocuments()) {
                batch.update(doc.getReference(), "role", "leader");
            }

            // 自分自身（元のリーダー）を user に戻す（対象が自分自身でない場合）
            if (!uid.equals(targetUserId)) {
                for (DocumentSnapshot doc : currentLeaderQuery.getDocuments()) {
                    batch.update(doc.getReference(), "role", "user");
                }
            }

            batch.commit().get();

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    targetUserName + " をリーダーにしました");

            return "redirect:/team_detail_page/" + teamId;

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー情報の変更に失敗しました");
            return "redirect:/team_detail_page/" + teamId;
        }
    }
    // メンバーをチームから外す
    @PostMapping("/team/{teamId}/members/remove")
    public String removeMember(
            @PathVariable("teamId") String teamId,
            @RequestParam("targetUserId") String targetUserId,
            @RequestParam("targetUserName") String targetUserName,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            String uid = (String) session.getAttribute("loginUid");
            if (uid == null) {
                return "redirect:/login";
            }

            if (!isLeader(teamId, uid)) {
                return "redirect:/settings";
            }

            QuerySnapshot targetQuery = firestore.collection("teammembers")
                    .whereEqualTo("teamId", teamId)
                    .whereEqualTo("userId", targetUserId)
                    .get()
                    .get();

            if (targetQuery.isEmpty()) {
                redirectAttributes.addFlashAttribute(
                        "errorMessage",
                        "ユーザー情報の変更に失敗しました");
                return "redirect:/team_detail_page/" + teamId;
            }

            WriteBatch batch = firestore.batch();
            for (DocumentSnapshot doc : targetQuery.getDocuments()) {
                batch.delete(doc.getReference());
            }
            batch.commit().get();

            redirectAttributes.addFlashAttribute(
                    "successMessage",
                    targetUserName + " をチームから外しました");

            return "redirect:/team_detail_page/" + teamId;

        } catch (Exception e) {
            e.printStackTrace();
            redirectAttributes.addFlashAttribute(
                    "errorMessage",
                    "ユーザー情報の変更に失敗しました");
            return "redirect:/team_detail_page/" + teamId;
        }
    }

    private boolean isLeader(String teamId, String uid) throws Exception {
        QuerySnapshot leaderResult = firestore.collection("teammembers")
                .whereEqualTo("teamId", teamId)
                .whereEqualTo("userId", uid)
                .whereEqualTo("role", "leader")
                .get()
                .get();
        return !leaderResult.isEmpty();
    }
}
}
