package com.ot.backend.component;

import com.ot.backend.entity.Role;
import com.ot.backend.entity.User;
import com.ot.backend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AuthSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (userRepository.findByUsername("superadmin").isEmpty()) {
            User superAdmin = new User();
            superAdmin.setFullname("Super Administrator");
            superAdmin.setUsername("superadmin");
            superAdmin.setPassword(passwordEncoder.encode("Super@123"));
            superAdmin.setRole(Role.SUPER_ADMIN);
            superAdmin.setActive(true);
            userRepository.save(superAdmin);
        }

        if (userRepository.findByUsername("admin").isEmpty()) {
            User admin = new User();
            admin.setFullname("System Admin");
            admin.setUsername("admin");
            admin.setPassword(passwordEncoder.encode("Admin@123"));
            admin.setRole(Role.ADMIN);
            admin.setActive(true);
            userRepository.save(admin);
        }

        if (userRepository.findByUsername("technician").isEmpty()) {
            User technician = new User();
            technician.setFullname("Lead Technician");
            technician.setUsername("technician");
            technician.setPassword(passwordEncoder.encode("Tech@123"));
            technician.setRole(Role.TECHNICIAN);
            technician.setActive(true);
            userRepository.save(technician);
        }

        if (userRepository.findByUsername("doctor").isEmpty()) {
            User doctor = new User();
            doctor.setFullname("Head Surgeon");
            doctor.setUsername("doctor");
            doctor.setPassword(passwordEncoder.encode("Doctor@123"));
            doctor.setRole(Role.DOCTOR);
            doctor.setActive(true);
            userRepository.save(doctor);
            System.out.println("✅ Security Seeder Finished: Injected Missing Core User Roles into SQLite.");
        }
    }
}
