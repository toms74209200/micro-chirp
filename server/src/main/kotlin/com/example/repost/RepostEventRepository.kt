package com.example.repost

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface RepostEventRepository : JpaRepository<RepostEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<RepostEvent>
}
