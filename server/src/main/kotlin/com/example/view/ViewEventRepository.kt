package com.example.view

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

interface ViewCountByPostId {
    fun getPostId(): UUID
    fun getCount(): Long
}

@Repository
interface ViewEventRepository : JpaRepository<ViewEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<ViewEvent>

    fun countByPostId(postId: UUID): Long

    @Query("SELECT v.postId as postId, COUNT(v) as count FROM ViewEvent v WHERE v.postId IN :postIds GROUP BY v.postId")
    fun countsByPostIdIn(postIds: Collection<UUID>): List<ViewCountByPostId>
}
