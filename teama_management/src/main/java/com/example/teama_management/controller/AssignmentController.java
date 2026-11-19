package com.example.teama_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AssignmentController {

    @GetMapping("/assignments/create")
    public String showAssignmentmanagementPage() {
        return "Assignment_management";
    }

    @GetMapping("/assignments/edit")
    public String showAssignmenteditPage(
            @RequestParam(value = "id", required = false) String id,
            Model model
    ) {
        model.addAttribute("assignmentId", id);
        return "Assignment_edit";
    }
}