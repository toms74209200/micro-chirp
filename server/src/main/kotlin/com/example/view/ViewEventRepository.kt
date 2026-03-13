package com.example.view

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ViewEventRepository : JpaRepository<ViewEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<ViewEvent>

    fun findByPostIdInOrderByOccurredAtAsc(postIds: Collection<UUID>): List<ViewEvent>
}
