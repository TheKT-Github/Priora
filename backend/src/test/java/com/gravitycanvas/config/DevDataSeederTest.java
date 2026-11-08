package com.gravitycanvas.config;

import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "priora.demo-data.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:dev_seeder_isolated_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class DevDataSeederTest {

    @Autowired
    private UserAccountRepository userRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired(required = false)
    private DevDataSeeder seeder;

    @Test
    @DisplayName("DevDataSeeder seeds 4 users and 6 tasks (5 approved, 1 pending) with valid BCrypt passwords")
    void testDevDataSeederExecution() {
        assertNotNull(seeder, "DevDataSeeder bean must be active when priora.demo-data.enabled=true");

        // Verify users
        assertEquals(4, userRepository.count(), "Must seed exactly 4 demo users");
        assertTrue(userRepository.existsByUsername("project.manager"));
        assertTrue(userRepository.existsByUsername("team.lead"));
        assertTrue(userRepository.existsByUsername("developer.one"));
        assertTrue(userRepository.existsByUsername("developer.two"));

        // Verify BCrypt password
        var pm = userRepository.findByUsername("project.manager").orElseThrow();
        assertEquals("ROLE_PROJECT_MANAGER", pm.getRole());
        assertTrue(passwordEncoder.matches("PrioraDemo2026!", pm.getPassword()),
                "Demo password must match raw string via BCrypt encoder");

        var lead = userRepository.findByUsername("team.lead").orElseThrow();
        assertEquals("ROLE_TEAM_LEAD", lead.getRole());
        assertTrue(passwordEncoder.matches("PrioraDemo2026!", lead.getPassword()));

        var dev1 = userRepository.findByUsername("developer.one").orElseThrow();
        assertEquals("ROLE_TEAM_MEMBER", dev1.getRole());
        assertTrue(passwordEncoder.matches("PrioraDemo2026!", dev1.getPassword()));

        // Verify tasks
        List<Task> approved = taskRepository.findAllApprovedWithSingleValued();
        List<Task> pending = taskRepository.findAllPendingWithSingleValued();
        assertEquals(5, approved.size(), "Must seed exactly 5 approved tasks");
        assertEquals(1, pending.size(), "Must seed exactly 1 pending task");
        assertEquals(6, taskRepository.count(), "Total tasks must be 6");

        // Verify idempotency: running seeder again should not duplicate anything
        seeder.run();
        assertEquals(4, userRepository.count(), "User count must remain unchanged after re-run");
        assertEquals(6, taskRepository.count(), "Task count must remain unchanged after re-run");
    }
}
