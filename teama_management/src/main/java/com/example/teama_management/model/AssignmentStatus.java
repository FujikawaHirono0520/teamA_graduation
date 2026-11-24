package com.example.teama_management.model;

public enum AssignmentStatus {
    NOT_STARTED("notStarted", "未着手", "未着手", "st-gray"),
    IN_PROGRESS("inProgress", "着手中", "着手中", "st-orange"),
    WAITING_APPROVAL("waitingApproval", "承認待ち", "承認待ち", "st-blue"),
    RESUBMIT("resubmit", "再提出", "再提出", "st-red"),
    APPROVED("approved", "完了", "承認済み", "st-green");

    private final String code;
    private final String userLabel;
    private final String leaderLabel;
    private final String cssClass;

    AssignmentStatus(String code, String userLabel, String leaderLabel, String cssClass) {
        this.code = code;
        this.userLabel = userLabel;
        this.leaderLabel = leaderLabel;
        this.cssClass = cssClass;
    }

    public String code() { return code; }
    public String cssClass() { return cssClass; }
    public String label(boolean leader) { return leader ? leaderLabel : userLabel; }

    public static AssignmentStatus fromCode(String code) {
        for (AssignmentStatus s : values()) {
            if (s.code.equals(code)) return s;
        }
        return NOT_STARTED;
    }
}