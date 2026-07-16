package com.poshanforlife.api.config;

import com.poshanforlife.api.entity.Role;
import com.poshanforlife.api.entity.User;
import com.poshanforlife.api.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Local/dev-only seed so the portal is usable out of the box.
 * Never active in prod — production admins are provisioned by the
 * users feature (or manually).
 */
@Slf4j
@Component
@Profile({"local", "dev"})
@RequiredArgsConstructor
public class DevUserSeeder implements CommandLineRunner {

    private static final String SEED_EMAIL = "admin@poshanforlife.com";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.existsByEmail(SEED_EMAIL)) {
            return;
        }
        User admin = new User();
        admin.setName("Admin");
        admin.setEmail(SEED_EMAIL);
        admin.setPasswordHash(passwordEncoder.encode("Admin@123"));
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.warn("Seeded dev admin user {} with the default password — change it outside local dev", SEED_EMAIL);
    }
}
