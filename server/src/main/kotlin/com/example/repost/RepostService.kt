package com.example.repost

import com.example.post.PostEventRepository
import com.example.post.aggregatePostEvents
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface RepostResult {
    data class Success(
        val postId: UUID,
        val userId: UUID,
        val repostedAt: Instant,
    ) : RepostResult

    data object PostNotFound : RepostResult

    data class DataAccessFailure(
        val exception: Exception,
    ) : RepostResult
}

sealed interface UnrepostResult {
    data object Success : UnrepostResult

    data object PostNotFound : UnrepostResult

    data class DataAccessFailure(
        val exception: Exception,
    ) : UnrepostResult
}

class RepostPostNotFoundException(
    message: String,
) : Exception(message)

@Service
class RepostService(
    private val repostEventRepository: RepostEventRepository,
    private val postEventRepository: PostEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun repostPost(
        postId: UUID,
        userId: UUID,
    ): RepostResult {
        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return RepostResult.DataAccessFailure(e)
            }

        val aggregatedPost =
            aggregatePostEvents(postEvents, objectMapper)
                ?: return RepostResult.PostNotFound

        val occurredAt = Instant.now()

        val repostEvent =
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.REPOSTED.value,
                occurredAt = occurredAt,
            )

        return try {
            repostEventRepository.save(repostEvent)
            RepostResult.Success(
                postId = postId,
                userId = userId,
                repostedAt = occurredAt,
            )
        } catch (e: DataAccessException) {
            RepostResult.DataAccessFailure(e)
        }
    }

    @WithSpan
    fun unrepostPost(
        postId: UUID,
        userId: UUID,
    ): UnrepostResult {
        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnrepostResult.DataAccessFailure(e)
            }

        aggregatePostEvents(postEvents, objectMapper)
            ?: return UnrepostResult.PostNotFound

        val repostEvents =
            try {
                repostEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnrepostResult.DataAccessFailure(e)
            }

        val userRepostStatus = UserRepostStatus.fromEvents(repostEvents, userId)

        if (userRepostStatus is UserRepostStatus.NotReposted) {
            return UnrepostResult.Success
        }

        val unrepostEvent =
            RepostEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                eventType = RepostEventType.UNREPOSTED.value,
                occurredAt = Instant.now(),
            )

        return try {
            repostEventRepository.save(unrepostEvent)
            UnrepostResult.Success
        } catch (e: DataAccessException) {
            UnrepostResult.DataAccessFailure(e)
        }
    }
}
