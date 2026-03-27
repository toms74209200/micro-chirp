package com.example.timeline

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "mv_refresh_log")
class MvRefreshLog(
    @Id
    @Column(name = "view_name", length = 100)
    val viewName: String,
    @Column(name = "last_refreshed_at", nullable = false)
    var lastRefreshedAt: Instant,
)
