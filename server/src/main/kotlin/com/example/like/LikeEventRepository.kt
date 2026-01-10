package com.example.like

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface LikeEventRepository : JpaRepository<LikeEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<LikeEvent>
}
