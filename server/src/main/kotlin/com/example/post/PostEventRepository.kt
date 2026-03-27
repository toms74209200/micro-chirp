package com.example.post

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface PostEventRepository : JpaRepository<PostEvent, UUID> {
    fun findByPostIdOrderByOccurredAtAsc(postId: UUID): List<PostEvent>

    fun findByReplyToPostIdOrderByOccurredAtAsc(replyToPostId: UUID): List<PostEvent>

    fun findByPostIdInOrderByOccurredAtAsc(postIds: Collection<UUID>): List<PostEvent>

    fun findByReplyToPostIdInOrderByOccurredAtAsc(replyToPostIds: Collection<UUID>): List<PostEvent>

    fun findByOccurredAtAfterOrderByOccurredAtAsc(occurredAt: java.time.Instant): List<PostEvent>

    fun findFirstByPostIdAndEventType(
        postId: UUID,
        eventType: String,
    ): PostEvent?
}
