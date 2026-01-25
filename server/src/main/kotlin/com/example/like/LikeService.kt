package com.example.like

import com.example.auth.UserRepository
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

    data class Failure(
        val exception: Exception,
    ) : LikeResult
}

sealed interface UnlikeResult {
    data object Success : UnlikeResult

    data class Failure(
        val exception: Exception,
    ) : UnlikeResult
}

class LikePostNotFoundException(
    message: String,
) : Exception(message)

class LikeUserNotFoundException(
    message: String,
) : Exception(message)

@Service
class LikeService(
    private val likeEventRepository: LikeEventRepository,
    private val postEventRepository: PostEventRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun likePost(
        postId: UUID,
        userId: UUID,
    ): LikeResult {
        if (!userRepository.existsById(userId)) {
            return LikeResult.Failure(LikeUserNotFoundException("User not found"))
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return LikeResult.Failure(e)
            }

        val aggregatedPost =
            aggregatePostEvents(postEvents, objectMapper)
                ?: return LikeResult.Failure(LikePostNotFoundException("Post not found"))

        val occurredAt = Instant.now()

        val likeEvent =
            LikeEvent(
                eventId = UUID.randomUUID(),
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
            LikeResult.Failure(e)
        }
    }

    @WithSpan
    fun unlikePost(
        postId: UUID,
        userId: UUID,
    ): UnlikeResult {
        if (!userRepository.existsById(userId)) {
            return UnlikeResult.Failure(LikeUserNotFoundException("User not found"))
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnlikeResult.Failure(e)
            }

        aggregatePostEvents(postEvents, objectMapper)
            ?: return UnlikeResult.Failure(LikePostNotFoundException("Post not found"))

        val likeEvents =
            try {
                likeEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnlikeResult.Failure(e)
            }

        val userLikeStatus = UserLikeStatus.fromEvents(likeEvents, userId)

        if (userLikeStatus is UserLikeStatus.NotLiked) {
            return UnlikeResult.Success
        }

        val unlikeEvent =
            LikeEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = LikeEventType.UNLIKED.value,
                occurredAt = Instant.now(),
            )

        return try {
            likeEventRepository.save(unlikeEvent)
            UnlikeResult.Success
        } catch (e: DataAccessException) {
            UnlikeResult.Failure(e)
        }
    }
}
