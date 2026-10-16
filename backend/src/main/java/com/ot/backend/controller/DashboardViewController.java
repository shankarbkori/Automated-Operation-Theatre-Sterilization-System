package com.ot.backend.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardViewController {

    @GetMapping("/")
    public String home() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String dashboard() {
        return "dashboard";
    }

    @GetMapping("/live-monitoring")
    public String liveSensors() {
        return "live-monitoring";
    }

    @GetMapping("/sterilization-control")
    public String sterilization() {
        return "sterilization-control";
    }

    @GetMapping("/history")
    public String history() {
        return "history";
    }

    @GetMapping("/reports")
    public String reports() {
        return "reports";
    }

    @GetMapping("/ai-insights")
    public String aiInsights() {
        return "ai-insights";
    }

    @GetMapping("/settings")
    public String settings() {
        return "settings";
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }
}