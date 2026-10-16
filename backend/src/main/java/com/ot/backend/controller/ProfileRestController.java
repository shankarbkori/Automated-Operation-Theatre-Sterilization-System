package com.ot.backend.controller;

import com.ot.backend.entity.User;
import com.ot.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
public class ProfileRestController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping("/me")
    public ResponseEntity<?> getMyProfile() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent()) {
            User u = user.get();
            // Don't expose password
            u.setPassword(null);
            return ResponseEntity.ok(u);
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/me/password")
    public ResponseEntity<?> changePassword(@RequestBody Map<String, String> payload) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String oldPass = payload.get("oldPassword");
            String newPass = payload.get("newPassword");

            if (passwordEncoder.matches(oldPass, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(newPass));
                userRepository.save(user);
                return ResponseEntity.ok(Map.of("success", true, "message", "Password updated successfully"));
            } else {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "Incorrect old password"));
            }
        }
        return ResponseEntity.status(401).build();
    }

    @PostMapping("/me/update")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, String> payload) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Optional<User> userOpt = userRepository.findByUsername(username);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            user.setFullname(payload.get("fullname"));
            user.setEmail(payload.get("email"));
            user.setPhone(payload.get("phone"));
            user.setDepartment(payload.get("department"));
            userRepository.save(user);
            return ResponseEntity.ok(Map.of("success", true, "message", "Profile updated successfully"));
        }
        return ResponseEntity.status(401).build();
    }
}
