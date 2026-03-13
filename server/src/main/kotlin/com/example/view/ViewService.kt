package com.example.view

import com.example.auth.UserRepository
import com.example.post.PostEventRepository
import com.example.post.aggregatePostEvents
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface ViewResult {
    data class Success(
        val postId: UUID,
        val userId: UUID,
        val viewedAt: Instant,
    ) : ViewResult

    data class Failure(
        val exception: Exception,
    ) : ViewResult
}

class ViewUserNotFoundException(
    message: String,
) : Exception(message)

class ViewPostNotFoundException(
    message: String,
) : Exception(message)

@Service
class ViewService(
    private val viewEventRepository: ViewEventRepository,
    private val postEventRepository: PostEventRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun recordView(
        postId: UUID,
        userId: UUID,
    ): ViewResult {
        if (!userRepository.existsById(userId)) {
            return ViewResult.Failure(ViewUserNotFoundException("User not found"))
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return ViewResult.Failure(e)
            }

        aggregatePostEvents(postEvents, objectMapper)
            ?: return ViewResult.Failure(ViewPostNotFoundException("Post not found"))

        val occurredAt = Instant.now()

        val viewEvent =
            ViewEvent(
                eventId = UUID.randomUUID(),
                postId = postId,
                userId = userId,
                occurredAt = occurredAt,
            )

        return try {
            viewEventRepository.save(viewEvent)
            ViewResult.Success(
                postId = postId,
                userId = userId,
                viewedAt = occurredAt,
            )
        } catch (e: DataAccessException) {
            ViewResult.Failure(e)
        }
    }
}
