package com.workly.service;

import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TaskAssignmentSchemaMigration implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;

    public TaskAssignmentSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        migrateTaskAssignmentStatusColumn();
    }

    private void migrateTaskAssignmentStatusColumn() {
        try {
            String columnType = jdbcTemplate.query(
                "SHOW COLUMNS FROM task_assignments LIKE 'status'",
                rs -> rs.next() ? rs.getString("Type") : null
            );

            if (columnType != null && columnType.toLowerCase().startsWith("enum(")) {
                jdbcTemplate.execute(
                    "ALTER TABLE task_assignments MODIFY COLUMN status VARCHAR(50) NOT NULL"
                );
            }
        } catch (Exception ignored) {
            // Ignore migration errors here so local startup is not blocked
            // when the table is not created yet. Hibernate will create it first.
        }
    }
}
