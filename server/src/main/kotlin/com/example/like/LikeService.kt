package com.example.like

import com.example.post.PostEventRepository
import com.example.post.aggregatePostEvents
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface LikeResult {
    data class Success(
        val postId: UUID,
        val userId: UUID,
        val likedAt: Instant,
    ) : LikeResult

    data object PostNotFound : LikeResult

    data class DataAccessFailure(
        val exception: Exception,
    ) : LikeResult
}

class LikePostNotFoundException(
    message: String,
) : Exception(message)

@Service
class LikeService(
    private val likeEventRepository: LikeEventRepository,
    private val postEventRepository: PostEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun likePost(
        postId: UUID,
        userId: UUID,
    ): LikeResult {
        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return LikeResult.DataAccessFailure(e)
            }

        val aggregatedPost = aggregatePostEvents(postEvents, objectMapper)
            ?: return LikeResult.PostNotFound

        val eventId = UUID.randomUUID()
        val occurredAt = Instant.now()

        val likeEvent =
            LikeEvent(
                eventId = eventId,
                postId = postId,
                userId = userId,
                eventType = LikeEventType.LIKED.value,
                occurredAt = occurredAt,
            )

        return try {
            likeEventRepository.save(likeEvent)
            LikeResult.Success(
                postId = postId,
                userId = userId,
                likedAt = occurredAt,
            )
        } catch (e: DataAccessException) {
            LikeResult.DataAccessFailure(e)
        }
    }
}
