package com.example.post

import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface PostCreationResult {
    data class Success(
        val postId: UUID,
        val userId: UUID,
        val content: String,
        val createdAt: Instant,
    ) : PostCreationResult

    data class ValidationFailure(
        val errorMessage: String,
    ) : PostCreationResult

    data class DataAccessFailure(
        val exception: Exception,
    ) : PostCreationResult
}

class ValidationException(
    message: String,
) : Exception(message)

@Service
class PostService(
    private val postEventRepository: PostEventRepository,
    private val userRepository: com.example.auth.UserRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun createPost(
        userId: UUID,
        rawContent: String,
    ): PostCreationResult {
        val validatedContent =
            parsePostContent(rawContent)
                ?: return PostCreationResult.ValidationFailure("Content is invalid")

        if (!userRepository.existsById(userId)) {
            return PostCreationResult.ValidationFailure("User not found")
        }

        val postId = UUID.randomUUID()
        val eventId = UUID.randomUUID()
        val occurredAt = Instant.now()

        val eventData =
            mapOf(
                "userId" to userId.toString(),
                "content" to validatedContent.value,
            )
        val eventDataJson = objectMapper.writeValueAsString(eventData)

        val postEvent =
            PostEvent(
                eventId = eventId,
                postId = postId,
                eventType = "post_created",
                eventData = eventDataJson,
                occurredAt = occurredAt,
            )

        return try {
            postEventRepository.save(postEvent)
            PostCreationResult.Success(
                postId = postId,
                userId = userId,
                content = validatedContent.value,
                createdAt = occurredAt,
            )
        } catch (e: DataAccessException) {
            PostCreationResult.DataAccessFailure(e)
        }
    }
}
