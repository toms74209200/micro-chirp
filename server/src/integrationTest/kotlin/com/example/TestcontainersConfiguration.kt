package com.example

import org.springframework.boot.ApplicationRunner
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.jdbc.core.JdbcTemplate
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    @ServiceConnection
    fun postgresContainer() = PostgreSQLContainer(DockerImageName.parse("postgres:18"))

    @Bean
    fun globalTimelineMvInitializer(jdbcTemplate: JdbcTemplate) =
        ApplicationRunner {
            jdbcTemplate.execute(
                """
                CREATE MATERIALIZED VIEW IF NOT EXISTS global_timeline_mv AS
                WITH created AS (
                    SELECT
                        pe.post_id,
                        (pe.event_data->>'userId')::uuid AS user_id,
                        pe.event_data->>'content' AS content,
                        pe.occurred_at AS created_at
                    FROM post_events pe
                    WHERE pe.event_type = 'post_created'
                ),
                deleted AS (
                    SELECT DISTINCT post_id FROM post_events WHERE event_type = 'post_deleted'
                )
                SELECT c.post_id, c.user_id, c.content, c.created_at
                FROM created c
                WHERE c.post_id NOT IN (SELECT post_id FROM deleted)
                ORDER BY c.created_at DESC
                """.trimIndent(),
            )
            jdbcTemplate.execute(
                "CREATE UNIQUE INDEX IF NOT EXISTS idx_global_timeline_mv_post_id ON global_timeline_mv (post_id)",
            )
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_global_timeline_mv_created_at ON global_timeline_mv (created_at DESC)",
            )
            jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_global_timeline_mv_user_id ON global_timeline_mv (user_id, created_at DESC)",
            )
            jdbcTemplate.execute(
                "INSERT INTO mv_refresh_log (view_name, last_refreshed_at) VALUES ('global_timeline_mv', current_timestamp) ON CONFLICT DO NOTHING",
            )
        }
}
