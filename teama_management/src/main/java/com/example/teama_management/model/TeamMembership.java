package com.example.teama_management.model;

public record TeamMembership(String teamId, String teamName, String role) {
    public boolean isLeader() {
        return "leader".equals(role);
    }
}