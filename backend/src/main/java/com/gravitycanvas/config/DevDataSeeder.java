package com.gravitycanvas.config;

import com.gravitycanvas.entity.ApprovalStatus;
import com.gravitycanvas.entity.Task;
import com.gravitycanvas.entity.TaskEnvironment;
import com.gravitycanvas.entity.TaskStatus;
import com.gravitycanvas.entity.UserAccount;
import com.gravitycanvas.repository.TaskRepository;
import com.gravitycanvas.repository.UserAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Development-only Data Seeder for local demonstration and onboarding.
 *
 * <p>Strictly restricted to the {@code dev} profile and conditionally activated via
 * {@code priora.demo-data.enabled=true}. It is explicitly disabled in test and production profiles.</p>
 *
 * <p>Seeds four demonstration user accounts across all system roles:
 * <ul>
 *   <li>{@code project.manager} (ROLE_PROJECT_MANAGER) - Marcus Vance</li>
 *   <li>{@code team.lead} (ROLE_TEAM_LEAD) - Elena Rostova</li>
 *   <li>{@code developer.one} (ROLE_TEAM_MEMBER) - David Chen</li>
 *   <li>{@code developer.two} (ROLE_TEAM_MEMBER) - Sarah Jenkins</li>
 * </ul>
 * All demo accounts share the local development password: {@code PrioraDemo2026!} (BCrypt hashed).
 * </p>
 *
 * <p>Provisions a realistic, cycle-free 5-task approved diamond DAG plus 1 pending-approval task:
 * <pre>
 *   Task 1 (DONE) -> Task 2 (IN_PROGRESS) -> [ Task 3 (IN_PROGRESS), Task 4 (TODO) ] -> Task 5 (TODO)
 *   Task 6 (TODO, PENDING_APPROVAL) - isolated queue demo
 * </pre>
 * </p>
 */
@Component
@Profile("dev")
@ConditionalOnProperty(name = "priora.demo-data.enabled", havingValue = "true", matchIfMissing = false)
public class DevDataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    public static final String DEMO_PASSWORD = "PrioraDemo2026!";

    private static final Set<String> DEMO_USERNAMES = Set.of(
            "project.manager",
            "team.lead",
            "developer.one",
            "developer.two"
    );

    private static final int EXPECTED_DEMO_USERS = 4;
    private static final int EXPECTED_APPROVED_TASKS = 5;
    private static final int EXPECTED_PENDING_TASKS = 1;
    private static final int EXPECTED_TOTAL_TASKS = EXPECTED_APPROVED_TASKS + EXPECTED_PENDING_TASKS;

    private final UserAccountRepository userRepository;
    private final TaskRepository taskRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    public DevDataSeeder(UserAccountRepository userRepository,
                         TaskRepository taskRepository,
                         PasswordEncoder passwordEncoder,
                         PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(String... args) {
        log.info("Evaluating Priora development demo data seeding requirements...");
        transactionTemplate.executeWithoutResult(status -> {
            try {
                processSeeding();
            } catch (Exception ex) {
                log.error("Failed to seed development demo data. Rolling back transaction.", ex);
                status.setRollbackOnly();
                throw ex;
            }
        });
    }

    private void processSeeding() {
        long totalUsers = userRepository.count();
        long totalTasks = taskRepository.count();

        // Check if database is completely empty (clean local startup)
        if (totalUsers == 0 && totalTasks == 0) {
            log.info("Database is empty. Executing clean demo dataset initialization...");
            performSeed();
            return;
        }

        // Check how many designated demo users exist
        long existingDemoUsers = DEMO_USERNAMES.stream()
                .filter(userRepository::existsByUsername)
                .count();

        // Robust idempotency check: already fully initialized
        if (existingDemoUsers == EXPECTED_DEMO_USERS && totalTasks == EXPECTED_TOTAL_TASKS) {
            log.info("Priora demo data is already fully initialized ({} demo users, {} tasks). Skipping seed.",
                    existingDemoUsers, totalTasks);
            return;
        }

        // Incomplete seed data detection: report rather than silently skipping or overwriting
        if (existingDemoUsers > 0 || (totalTasks > 0 && totalTasks < EXPECTED_TOTAL_TASKS)) {
            String warningMsg = String.format(
                    "INCOMPLETE DEMO DATA DETECTED: Found %d of %d demo users and %d of %d expected demo tasks. " +
                    "Refusing to overwrite or partially seed to preserve database integrity. " +
                    "Please restart the application with a clean in-memory H2 database.",
                    existingDemoUsers, EXPECTED_DEMO_USERS, totalTasks, EXPECTED_TOTAL_TASKS
            );
            log.warn(warningMsg);
            return;
        }

        // Existing custom user accounts or tasks detected
        log.info("Existing application data detected ({} users, {} tasks). " +
                "Refusing to overwrite existing user accounts or application data.",
                totalUsers, totalTasks);
    }

    private void performSeed() {
        // 1. Seed demo user accounts
        String encodedPassword = passwordEncoder.encode(DEMO_PASSWORD);

        UserAccount pm = new UserAccount(
                null, "project.manager", encodedPassword, "ROLE_PROJECT_MANAGER",
                "Marcus Vance", "EMP-001", "Product & Engineering"
        );
        pm = userRepository.save(pm);

        UserAccount tl = new UserAccount(
                null, "team.lead", encodedPassword, "ROLE_TEAM_LEAD",
                "Elena Rostova", "EMP-002", "Platform Engineering"
        );
        tl = userRepository.save(tl);

        UserAccount dev1 = new UserAccount(
                null, "developer.one", encodedPassword, "ROLE_TEAM_MEMBER",
                "David Chen", "EMP-003", "Core Backend"
        );
        dev1 = userRepository.save(dev1);

        UserAccount dev2 = new UserAccount(
                null, "developer.two", encodedPassword, "ROLE_TEAM_MEMBER",
                "Sarah Jenkins", "EMP-004", "Frontend Architecture"
        );
        dev2 = userRepository.save(dev2);

        userRepository.flush();
        log.info("Seeded {} demo user accounts successfully.", EXPECTED_DEMO_USERS);

        // 2. Seed realistic diamond task DAG
        LocalDateTime now = LocalDateTime.now();

        // Task 1: Root task (DONE, upstream blocker)
        Task task1 = createTask(
                "Database Schema Migrations & Baseline Indexing",
                "Define initial Flyway migration scripts, establish composite B-tree indexes, and validate PostgreSQL compatibility.",
                TaskStatus.DONE,
                ApprovalStatus.APPROVED,
                TaskEnvironment.PRODUCTION,
                dev1,
                pm,
                now.minusDays(1),
                0,
                false
        );
        task1 = taskRepository.save(task1);

        // Task 2: Dependent on Task 1 (IN_PROGRESS)
        Task task2 = createTask(
                "REST API Filter Chain & Role-Based Access Control",
                "Implement Spring Security 6 filter chain, BCrypt credential hashing, and endpoint authorization matrix.",
                TaskStatus.IN_PROGRESS,
                ApprovalStatus.APPROVED,
                TaskEnvironment.STAGING,
                dev1,
                tl,
                now.plusDays(2),
                1,
                true
        );
        task2 = taskRepository.save(task2);

        // Task 3: Branch A dependent on Task 2 (IN_PROGRESS)
        Task task3 = createTask(
                "Interactive Canvas Real-Time State Architecture",
                "Construct reactive Angular 16 canvas state service with signals, node placement, and zoom-to-fit geometry.",
                TaskStatus.IN_PROGRESS,
                ApprovalStatus.APPROVED,
                TaskEnvironment.DEVELOPMENT,
                dev2,
                tl,
                now.plusDays(4),
                0,
                false
        );
        task3 = taskRepository.save(task3);

        // Task 4: Branch B dependent on Task 2 (TODO)
        Task task4 = createTask(
                "Dynamic Gravity Engine Mathematical Modeling",
                "Implement continuous sigmoid deadline proximity, logarithmic blast radius, and stagnation acceleration.",
                TaskStatus.TODO,
                ApprovalStatus.APPROVED,
                TaskEnvironment.DEVELOPMENT,
                dev1,
                tl,
                now.plusDays(5),
                0,
                false
        );
        task4 = taskRepository.save(task4);

        // Task 5: Sink / Join dependent on Task 3 and Task 4 (TODO)
        Task task5 = createTask(
                "End-to-End Integration Test Suite & Release Verification",
                "Automate regression testing across the multi-root BFS traversal pipeline, canvas hydration, and security filters.",
                TaskStatus.TODO,
                ApprovalStatus.APPROVED,
                TaskEnvironment.STAGING,
                dev2,
                pm,
                now.plusDays(7),
                0,
                false
        );
        task5 = taskRepository.save(task5);

        // Task 6: Independent task awaiting manager approval
        Task task6 = createTask(
                "Real-Time WebSocket Gravity Sync Prototype",
                "Proposal for real-time collaborative canvas cursor sync and live gravity force updates across active sessions.",
                TaskStatus.TODO,
                ApprovalStatus.PENDING_APPROVAL,
                TaskEnvironment.DEVELOPMENT,
                dev2,
                dev2,
                now.plusDays(10),
                0,
                false
        );
        task6 = taskRepository.save(task6);

        // 3. Establish DAG dependencies (Parent -> Dependent child)
        // Task 1 -> Task 2
        task1.getDependentTasks().add(task2);
        task2.getDependencies().add(task1);

        // Task 2 -> Task 3 and Task 4
        task2.getDependentTasks().add(task3);
        task2.getDependentTasks().add(task4);
        task3.getDependencies().add(task2);
        task4.getDependencies().add(task2);

        // Task 3 and Task 4 -> Task 5
        task3.getDependentTasks().add(task5);
        task4.getDependentTasks().add(task5);
        task5.getDependencies().add(task3);
        task5.getDependencies().add(task4);

        taskRepository.saveAll(List.of(task1, task2, task3, task4, task5, task6));
        taskRepository.flush();

        // 4. Assert integrity of the newly seeded dataset
        List<Task> approvedTasks = taskRepository.findAllApprovedWithSingleValued();
        List<Task> pendingTasks = taskRepository.findAllPendingWithSingleValued();

        if (approvedTasks.size() != EXPECTED_APPROVED_TASKS || pendingTasks.size() != EXPECTED_PENDING_TASKS) {
            throw new IllegalStateException(String.format(
                    "Integrity verification failure: expected %d approved and %d pending tasks, but found %d approved and %d pending.",
                    EXPECTED_APPROVED_TASKS, EXPECTED_PENDING_TASKS, approvedTasks.size(), pendingTasks.size()
            ));
        }

        log.info("Priora demo data seeding complete: {} users created, {} approved tasks (DAG), {} pending task.",
                EXPECTED_DEMO_USERS, approvedTasks.size(), pendingTasks.size());
    }

    private Task createTask(String title,
                            String description,
                            TaskStatus status,
                            ApprovalStatus approvalStatus,
                            TaskEnvironment environment,
                            UserAccount assignee,
                            UserAccount createdBy,
                            LocalDateTime deadline,
                            int pingCount,
                            boolean pinged) {
        Task t = new Task();
        t.setTitle(title);
        t.setDescription(description);
        t.setStatus(status);
        t.setApprovalStatus(approvalStatus);
        t.setEnvironment(environment);
        t.setAssignee(assignee);
        t.setCreatedBy(createdBy);
        t.setDeadline(deadline);
        t.setPingCount(pingCount);
        t.setPinged(pinged);
        t.setCreatedAt(LocalDateTime.now().minusDays(5));
        t.setLastActivityAt(LocalDateTime.now().minusHours(2));
        t.setCustomFactorApplied(false);
        t.setDependencies(new HashSet<>());
        t.setDependentTasks(new HashSet<>());
        return t;
    }
}
