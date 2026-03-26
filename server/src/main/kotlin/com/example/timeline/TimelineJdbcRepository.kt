package com.example.timeline

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.util.UUID

data class TimelinePostRow(
    val postId: UUID,
    val userId: UUID,
    val content: String,
    val createdAt: Instant,
)

@Repository
class TimelineJdbcRepository(
    private val jdbcTemplate: JdbcTemplate,
) {
    fun findGlobalTimeline(
        limit: Int,
        offset: Int,
    ): List<TimelinePostRow> =
        jdbcTemplate.query(
            "SELECT post_id, user_id, content, created_at FROM global_timeline_mv ORDER BY created_at DESC LIMIT ? OFFSET ?",
            { rs, _ -> rs.toTimelinePostRow() },
            limit,
            offset,
        )

    fun countGlobalTimeline(): Long =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM global_timeline_mv",
            Long::class.java,
        ) ?: 0L

    private fun ResultSet.toTimelinePostRow() =
        TimelinePostRow(
            postId = UUID.fromString(getString("post_id")),
            userId = UUID.fromString(getString("user_id")),
            content = getString("content"),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
