package com.example.like

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "like_events")
class LikeEvent(
    @Id
    @Column(name = "event_id")
    val eventId: UUID,
    @Column(name = "post_id", nullable = false)
    val postId: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "event_type", nullable = false, length = 50)
    val eventType: String,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
)
