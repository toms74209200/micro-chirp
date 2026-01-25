package com.example.repost

import com.example.auth.UserRepository
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

    data class Failure(
        val exception: Exception,
    ) : RepostResult
}

sealed interface UnrepostResult {
    data object Success : UnrepostResult

    data class Failure(
        val exception: Exception,
    ) : UnrepostResult
}

class RepostPostNotFoundException(
    message: String,
) : Exception(message)

class RepostUserNotFoundException(
    message: String,
) : Exception(message)

@Service
class RepostService(
    private val repostEventRepository: RepostEventRepository,
    private val postEventRepository: PostEventRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun repostPost(
        postId: UUID,
        userId: UUID,
    ): RepostResult {
        if (!userRepository.existsById(userId)) {
            return RepostResult.Failure(RepostUserNotFoundException("User not found"))
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return RepostResult.Failure(e)
            }

        val aggregatedPost =
            aggregatePostEvents(postEvents, objectMapper)
                ?: return RepostResult.Failure(RepostPostNotFoundException("Post not found"))

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
            RepostResult.Failure(e)
        }
    }

    @WithSpan
    fun unrepostPost(
        postId: UUID,
        userId: UUID,
    ): UnrepostResult {
        if (!userRepository.existsById(userId)) {
            return UnrepostResult.Failure(RepostUserNotFoundException("User not found"))
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnrepostResult.Failure(e)
            }

        aggregatePostEvents(postEvents, objectMapper)
            ?: return UnrepostResult.Failure(RepostPostNotFoundException("Post not found"))

        val repostEvents =
            try {
                repostEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return UnrepostResult.Failure(e)
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
            UnrepostResult.Failure(e)
        }
    }
}
