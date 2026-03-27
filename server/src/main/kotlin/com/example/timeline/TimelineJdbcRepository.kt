package com.example.timeline

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.sql.Timestamp
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
    fun findGlobalTimeline(limit: Int): List<TimelinePostRow> =
        jdbcTemplate.query(
            "SELECT post_id, user_id, content, created_at FROM posts_mv ORDER BY created_at DESC, post_id DESC LIMIT ?",
            { rs, _ -> rs.toTimelinePostRow() },
            limit,
        )

    fun findGlobalTimelineAfter(
        limit: Int,
        cursorCreatedAt: Instant,
        cursorPostId: UUID,
    ): List<TimelinePostRow> =
        jdbcTemplate.query(
            """
            SELECT post_id, user_id, content, created_at FROM posts_mv
            WHERE (created_at < ?) OR (created_at = ? AND post_id < ?::uuid)
            ORDER BY created_at DESC, post_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.toTimelinePostRow() },
            Timestamp.from(cursorCreatedAt),
            Timestamp.from(cursorCreatedAt),
            cursorPostId.toString(),
            limit,
        )

    fun findUserTimeline(
        userId: UUID,
        limit: Int,
    ): List<TimelinePostRow> =
        jdbcTemplate.query(
            "SELECT post_id, user_id, content, created_at FROM posts_mv WHERE user_id = ?::uuid ORDER BY created_at DESC, post_id DESC LIMIT ?",
            { rs, _ -> rs.toTimelinePostRow() },
            userId.toString(),
            limit,
        )

    fun findUserTimelineAfter(
        userId: UUID,
        limit: Int,
        cursorCreatedAt: Instant,
        cursorPostId: UUID,
    ): List<TimelinePostRow> =
        jdbcTemplate.query(
            """
            SELECT post_id, user_id, content, created_at FROM posts_mv
            WHERE user_id = ?::uuid
            AND ((created_at < ?) OR (created_at = ? AND post_id < ?::uuid))
            ORDER BY created_at DESC, post_id DESC
            LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.toTimelinePostRow() },
            userId.toString(),
            Timestamp.from(cursorCreatedAt),
            Timestamp.from(cursorCreatedAt),
            cursorPostId.toString(),
            limit,
        )

    private fun ResultSet.toTimelinePostRow() =
        TimelinePostRow(
            postId = UUID.fromString(getString("post_id")),
            userId = UUID.fromString(getString("user_id")),
            content = getString("content"),
            createdAt = getTimestamp("created_at").toInstant(),
        )
}
