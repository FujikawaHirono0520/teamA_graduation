package com.example.teama_management.model;

public record AssignmentItem(
    String id,
    String title,
    AssignmentStatus status,
    String deadlineText,
    String updatedText,
    String assigneeName,
    long deadlineMillis,
    long updatedMillis) {
}