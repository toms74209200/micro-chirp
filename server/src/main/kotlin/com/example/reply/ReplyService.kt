package com.example.reply

import com.example.auth.UserRepository
import com.example.post.PostEvent
import com.example.post.PostEventRepository
import com.example.post.PostEventType
import com.example.post.aggregatePostEvents
import com.example.post.parsePostContent
import io.opentelemetry.instrumentation.annotations.WithSpan
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.Instant
import java.util.UUID

sealed interface ReplyCreationResult {
    data class Success(
        val replyPostId: UUID,
        val replyToPostId: UUID,
        val userId: UUID,
        val content: String,
        val createdAt: Instant,
    ) : ReplyCreationResult

    data object PostNotFound : ReplyCreationResult

    data class ValidationFailure(
        val errorMessage: String,
    ) : ReplyCreationResult

    data class DataAccessFailure(
        val exception: Exception,
    ) : ReplyCreationResult
}

class ReplyPostNotFoundException(
    message: String,
) : Exception(message)

class ReplyValidationException(
    message: String,
) : Exception(message)

@Service
class ReplyService(
    private val postEventRepository: PostEventRepository,
    private val userRepository: UserRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun replyToPost(
        replyToPostId: UUID,
        userId: UUID,
        rawContent: String,
    ): ReplyCreationResult {
        val validatedContent =
            parsePostContent(rawContent)
                ?: return ReplyCreationResult.ValidationFailure("Content is invalid")

        if (!userRepository.existsById(userId)) {
            return ReplyCreationResult.ValidationFailure("User not found")
        }

        val postEvents =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(replyToPostId)
            } catch (e: DataAccessException) {
                return ReplyCreationResult.DataAccessFailure(e)
            }

        aggregatePostEvents(postEvents, objectMapper)
            ?: return ReplyCreationResult.PostNotFound

        val replyPostId = UUID.randomUUID()
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
                postId = replyPostId,
                replyToPostId = replyToPostId,
                eventType = PostEventType.POST_CREATED.value,
                eventData = eventDataJson,
                occurredAt = occurredAt,
            )

        return try {
            postEventRepository.save(postEvent)
            ReplyCreationResult.Success(
                replyPostId = replyPostId,
                replyToPostId = replyToPostId,
                userId = userId,
                content = validatedContent.value,
                createdAt = occurredAt,
            )
        } catch (e: DataAccessException) {
            ReplyCreationResult.DataAccessFailure(e)
        }
    }
}
