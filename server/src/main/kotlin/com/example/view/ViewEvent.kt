package com.example.view

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "view_events")
class ViewEvent(
    @Id
    @Column(name = "event_id")
    val eventId: UUID,
    @Column(name = "post_id", nullable = false)
    val postId: UUID,
    @Column(name = "user_id", nullable = false)
    val userId: UUID,
    @Column(name = "occurred_at", nullable = false)
    val occurredAt: Instant,
)
