package com.example.post

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "post_events")
class PostEvent(
    @Id
    @Column(name = "event_id")
    val eventId: UUID,
    @Column(name = "post_id", nullable = false)
    val postId: UUID,
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,
    @Column(name = "event_data", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val eventData: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
)
