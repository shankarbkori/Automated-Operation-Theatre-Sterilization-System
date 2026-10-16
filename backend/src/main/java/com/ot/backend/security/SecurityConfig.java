package com.ot.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtRequestFilter jwtRequestFilter;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.security.authentication.dao.DaoAuthenticationProvider authenticationProvider() {
        org.springframework.security.authentication.dao.DaoAuthenticationProvider authProvider = new org.springframework.security.authentication.dao.DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // Disable CSRF for purely stateless JWT flow
                .authenticationProvider(authenticationProvider()) // Attach BCrypt & Details service natively
                .authorizeHttpRequests(auth -> auth
                        // Public endpoints — ESP32 heartbeat, sensor POST, static assets, auth
                        .requestMatchers("/api/auth/**", "/api/login", "/login", "/css/**", "/js/**", "/assets/**",
                                "/images/**", "/api/device/heartbeat", "/api/sensors", "/api/device/*/plan")
                        .permitAll()
                        // Role-based restrictions per requirements
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/system/**").hasAnyRole("ADMIN", "TECHNICIAN", "SUPER_ADMIN")
                        .requestMatchers("/api/reports/**").hasAnyRole("ADMIN", "DOCTOR", "SUPER_ADMIN")
                        // Profile endpoint must be accessible to ALL authenticated users (before
                        // SUPER_ADMIN rule)
                        .requestMatchers("/api/users/me").authenticated()
                        .requestMatchers("/api/users/**", "/users").hasRole("SUPER_ADMIN")
                        // Sterilization & device control require login
                        .requestMatchers("/api/sterilization/**").authenticated()
                        .requestMatchers("/api/device/**").authenticated()
                        // General authenticated UI and APIs
                        .requestMatchers("/api/dashboard/**", "/dashboard", "/live-monitoring", "/ai-insights",
                                "/reports", "/history", "/settings")
                        .authenticated()
                        // DEFAULT: require authentication (was permitAll — security hole)
                        .anyRequest().authenticated())
                .sessionManagement(sess -> sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtRequestFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
