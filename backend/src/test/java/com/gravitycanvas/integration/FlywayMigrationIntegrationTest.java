package com.gravitycanvas.integration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 9 - Flyway Migration Integration Test.
 *
 * Uses a completely isolated H2 in-memory database (flyway_isolated_test_db) separate
 * from the shared prioratest database used by the main test suite.
 * Flyway runs both V1 and V2 from an empty schema; Hibernate validates the result.
 *
 * Key verifications:
 *   1. Flyway info() reports exactly 2 successful migrations (V1 and V2).
 *   2. No baseline markers exist (baseline-on-migrate=false).
 *   3. All four application tables exist and are queryable.
 *   4. Both Phase 8 performance indexes exist in information_schema.
 *   5. Key columns (VERSION, APPROVAL_STATUS) exist on the TASKS table.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:flyway_isolated_test_db;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration",
        "spring.flyway.baseline-on-migrate=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.h2.console.enabled=false",
        "priora.demo-data.enabled=false"
})
class FlywayMigrationIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Flyway flyway;

    // 1. Flyway migration history via Java API (avoids H2 table naming issues)

    @Test
    void flyway_reports_exactly_two_successful_migrations() {
        MigrationInfo[] appliedMigrations = Arrays.stream(flyway.info().applied())
                .toArray(MigrationInfo[]::new);

        assertThat(appliedMigrations)
                .as("Exactly 3 migrations (V1, V2, and V3) should be applied")
                .hasSize(3);

        MigrationInfo v1 = appliedMigrations[0];
        assertThat(v1.getVersion().toString())
                .as("First migration must be V1")
                .isEqualTo("1");
        assertThat(v1.getState())
                .as("V1 must be in SUCCESS state")
                .isEqualTo(MigrationState.SUCCESS);

        MigrationInfo v2 = appliedMigrations[1];
        assertThat(v2.getVersion().toString())
                .as("Second migration must be V2")
                .isEqualTo("2");
        assertThat(v2.getState())
                .as("V2 must be in SUCCESS state")
                .isEqualTo(MigrationState.SUCCESS);

        MigrationInfo v3 = appliedMigrations[2];
        assertThat(v3.getVersion().toString())
                .as("Third migration must be V3")
                .isEqualTo("3");
        assertThat(v3.getState())
                .as("V3 must be in SUCCESS state")
                .isEqualTo(MigrationState.SUCCESS);
    }

    @Test
    void flyway_has_no_pending_migrations() {
        MigrationInfo[] pending = flyway.info().pending();
        assertThat(pending)
                .as("All migrations must be applied — no pending migrations expected")
                .isEmpty();
    }

    @Test
    void flyway_has_no_failed_migrations() {
        long failedCount = Arrays.stream(flyway.info().all())
                .filter(m -> m.getState() == MigrationState.FAILED)
                .count();
        assertThat(failedCount)
                .as("There must be no failed migrations")
                .isZero();
    }

    // 2. All four application tables exist and are queryable

    @Test
    void users_table_exists_and_is_empty_on_fresh_schema() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM USERS", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void gravity_settings_table_exists_and_is_empty_on_fresh_schema() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM GRAVITY_SETTINGS", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void tasks_table_exists_and_is_empty_on_fresh_schema() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM TASKS", Integer.class);
        assertThat(count).isZero();
    }

    @Test
    void task_dependencies_table_exists_and_is_empty_on_fresh_schema() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM TASK_DEPENDENCIES", Integer.class);
        assertThat(count).isZero();
    }

    // 3. Phase 8 performance indexes exist

    @Test
    void phase8_index_on_tasks_assignee_status_exists() {
        List<Map<String, Object>> indexes = jdbc.queryForList(
                "SELECT index_name FROM information_schema.indexes "
                + "WHERE upper(table_name) = 'TASKS' "
                + "AND upper(index_name) = 'IDX_TASKS_ASSIGNEE_STATUS'");
        assertThat(indexes).as("Expected index IDX_TASKS_ASSIGNEE_STATUS on TASKS").hasSize(1);
    }

    @Test
    void phase8_index_on_task_dependencies_dependent_id_exists() {
        List<Map<String, Object>> indexes = jdbc.queryForList(
                "SELECT index_name FROM information_schema.indexes "
                + "WHERE upper(table_name) = 'TASK_DEPENDENCIES' "
                + "AND upper(index_name) = 'IDX_TASK_DEPS_DEPENDENT_ID'");
        assertThat(indexes).as("Expected index IDX_TASK_DEPS_DEPENDENT_ID on TASK_DEPENDENCIES").hasSize(1);
    }

    @Test
    void phase10_index_on_tasks_approval_created_id_exists() {
        List<Map<String, Object>> indexes = jdbc.queryForList(
                "SELECT index_name FROM information_schema.indexes "
                + "WHERE upper(table_name) = 'TASKS' "
                + "AND upper(index_name) = 'IDX_TASKS_APPROVAL_CREATED_ID'");
        assertThat(indexes).as("Expected index IDX_TASKS_APPROVAL_CREATED_ID on TASKS").hasSize(1);
    }

    // 4. Key column constraints

    @Test
    void tasks_table_has_version_column_for_optimistic_locking() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                + "WHERE upper(table_name) = 'TASKS' AND upper(column_name) = 'VERSION'");
        assertThat(cols).as("TASKS.VERSION column must exist").hasSize(1);
    }

    @Test
    void tasks_table_has_approval_status_column() {
        List<Map<String, Object>> cols = jdbc.queryForList(
                "SELECT column_name FROM information_schema.columns "
                + "WHERE upper(table_name) = 'TASKS' AND upper(column_name) = 'APPROVAL_STATUS'");
        assertThat(cols).as("TASKS.APPROVAL_STATUS column must exist").hasSize(1);
    }
}
