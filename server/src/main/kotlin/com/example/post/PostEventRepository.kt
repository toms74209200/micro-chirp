package com.example.post

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PostEventRepository : JpaRepository<PostEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<PostEvent>
}
