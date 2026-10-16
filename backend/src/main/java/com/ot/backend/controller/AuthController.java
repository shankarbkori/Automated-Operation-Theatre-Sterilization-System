package com.ot.backend.controller;

import com.ot.backend.dto.AuthRequest;
import com.ot.backend.dto.AuthResponse;
import com.ot.backend.entity.User;
import com.ot.backend.repository.UserRepository;
import com.ot.backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private UserDetailsService userDetailsService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/login")
    public ResponseEntity<?> createAuthenticationToken(@RequestBody AuthRequest authRequest) {

        Optional<User> userOpt = userRepository.findByUsername(authRequest.getUsername());
        if (userOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, "Invalid Username or Password", null, null, null, null));
        }

        User user = userOpt.get();

        // Check if account is inactive
        if (!user.isActive()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(false, "Your account has been disabled. Contact Administrator.", null,
                            null, null, null));
        }

        // Check if account is locked
        if (user.getLockTime() != null) {
            if (user.getLockTime().plusMinutes(15).isAfter(LocalDateTime.now())) {
                return ResponseEntity.status(HttpStatus.LOCKED)
                        .body(new AuthResponse(false,
                                "Account locked due to too many failed attempts. Try again in 15 minutes.", null,
                                null, null, null));
            } else {
                // Unlock account
                user.setLockTime(null);
                user.setFailedAttempts(0);
                userRepository.save(user);
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(authRequest.getUsername(), authRequest.getPassword()));

            // Success reset
            user.setFailedAttempts(0);
            user.setLockTime(null);
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

        } catch (DisabledException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(new AuthResponse(false, "Your account has been disabled. Contact Administrator.", null,
                            null, null, null));
        } catch (BadCredentialsException e) {
            // Register failed attempt
            int attempts = user.getFailedAttempts() + 1;
            user.setFailedAttempts(attempts);
            if (attempts >= 5) {
                user.setLockTime(LocalDateTime.now());
            }
            userRepository.save(user);

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, "Invalid Username or Password", null, null, null, null));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new AuthResponse(false, "Invalid Username or Password", null, null, null, null));
        }

        final UserDetails userDetails = userDetailsService.loadUserByUsername(authRequest.getUsername());
        final String jwt = jwtUtil.generateToken(userDetails, user.getRole().name());

        return ResponseEntity.ok(new AuthResponse(true, "Login Successful", jwt, user.getRole().name(),
                user.getFullname(), user.getUsername()));
    }
}
