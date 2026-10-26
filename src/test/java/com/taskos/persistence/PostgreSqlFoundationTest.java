package com.taskos.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "TASKOS_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class PostgreSqlFoundationTest {
    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void connectsToPostgreSqlAndAppliesFlywaySchemaMigration() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertEquals("PostgreSQL", connection.getMetaData().getDatabaseProductName());
        }
        Integer schemaCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.schemata WHERE schema_name = 'task_os'", Integer.class);
        assertEquals(1, schemaCount);
        Integer migrationCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE version = '15' AND success = true", Integer.class);
        assertEquals(1, migrationCount);
        Integer userTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'task_os' AND table_name = 'users'", Integer.class);
        assertEquals(1, userTableCount);
        Integer phaseNineTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'task_os' AND table_name IN ('goals','goal_measurements','okr_cycles','habits','habit_schedules','reviews')", Integer.class);
        assertEquals(6, phaseNineTableCount);
        Integer collaborationTableCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'task_os' AND table_name IN "
                        + "('workspaces','workspace_memberships','workspace_invitations','workspace_activity','task_assignments',"
                        + "'task_assignment_history','task_comments','task_comment_mentions','workspace_notifications')", Integer.class);
        assertEquals(9, collaborationTableCount);
        Integer sharedScopeColumns = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_schema = 'task_os' AND column_name = 'workspace_id' "
                        + "AND table_name IN ('projects','tasks')", Integer.class);
        assertEquals(2, sharedScopeColumns);
    }
}
