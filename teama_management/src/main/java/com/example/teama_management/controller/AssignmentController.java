package com.example.teama_management.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AssignmentController {

    @GetMapping("/assignments/create")
    public String showAssignmentPage() {
        return "Assignment_management";
    }
}