package com.sentragrid.config;

import com.sentragrid.entity.User;
import com.sentragrid.entity.enums.UserRole;
import com.sentragrid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class DemoDataInitializer implements ApplicationRunner {

    private static final String DEMO_PASSWORD = "password123";

    private final UserRepository userRepository;
    private final com.sentragrid.repository.PharmacyRepository pharmacyRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        repairUserRoleConstraint();
        upsertDemoUser("patient1", "patient1@sentinel.com", UserRole.PATIENT, null);
        upsertDemoUser("pharmacist1", "pharmacist1@sentinel.com", UserRole.PHARMACIST, 1L);
        upsertDemoUser("manager1", "manager1@sentinel.com", UserRole.PHARMACY_MANAGER, 1L);
        upsertDemoUser("admin1", "admin1@sentinel.com", UserRole.ADMIN, null);
        initializeBranchDemoStaff();
        log.info("Demo users initialization completed for local development environment.");
    }

    private void initializeBranchDemoStaff() {
        // Supported patient-search branches: each branch has its own pharmacist and manager.
        // This demonstrates strict branch isolation without granting any account network-wide access.
        String[] cities = {"puducherry", "chennai", "bengaluru"};
        String[][] branchLabels = {
                {"mg", "mission", "wch"},
                {"anna", "perambur", "anna2"},
                {"indira", "hsr", "banashankari"}
        };
        long pharmacyId = 1L;
        for (int cityIndex = 0; cityIndex < cities.length; cityIndex++) {
            for (String label : branchLabels[cityIndex]) {
                String suffix = cities[cityIndex] + "-" + label;
                upsertDemoUser("pharmacist-" + suffix, "pharmacist-" + suffix + "@sentinel.com", UserRole.PHARMACIST, pharmacyId);
                upsertDemoUser("manager-" + suffix, "manager-" + suffix + "@sentinel.com", UserRole.PHARMACY_MANAGER, pharmacyId);
                pharmacyId++;
            }
        }
    }

    private void upsertDemoUser(String username, String email, UserRole role, Long pharmacyId) {
        User user = userRepository.findByUsername(username)
                .orElseGet(() -> User.builder().username(username).build());

        user.setEmail(email);
        user.setRole(role);
        user.setPassword(passwordEncoder.encode(DEMO_PASSWORD));

        if (pharmacyId != null) {
            user.setPharmacy(pharmacyRepository.findById(pharmacyId).orElse(null));
        }

        userRepository.save(user);
    }

    private void repairUserRoleConstraint() {
        jdbcTemplate.execute("ALTER TABLE users DROP CONSTRAINT IF EXISTS users_role_check");
        jdbcTemplate.execute("""
                ALTER TABLE users
                ADD CONSTRAINT users_role_check
                CHECK (role IN ('PATIENT', 'PHARMACIST', 'PHARMACY_MANAGER', 'ADMIN'))
                """);
    }
}
