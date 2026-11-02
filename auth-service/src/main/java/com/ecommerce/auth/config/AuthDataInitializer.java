package com.ecommerce.auth.config;

import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthDataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthDataInitializer.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            log.info("Auth database already seeded with {} users", userRepository.count());
            return;
        }

        log.info("Seeding default demo customer and admin accounts into ecommerce_auth...");

        User customer = User.builder()
                .name("Demo Customer")
                .email("customer@test.com")
                .password(passwordEncoder.encode("Password123!"))
                .role(Role.ROLE_CUSTOMER)
                .build();

        User admin = User.builder()
                .name("Store Administrator")
                .email("admin@test.com")
                .password(passwordEncoder.encode("AdminPass123!"))
                .role(Role.ROLE_ADMIN)
                .build();

        userRepository.save(customer);
        userRepository.save(admin);

        log.info("Default user accounts created: customer@test.com / Password123! and admin@test.com / AdminPass123!");
    }
}
