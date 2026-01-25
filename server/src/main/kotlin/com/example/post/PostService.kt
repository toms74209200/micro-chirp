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

    data class Failure(
        val exception: Exception,
    ) : PostCreationResult
}

sealed interface PostRetrievalResult {
    data class Success(
        val postId: UUID,
        val userId: UUID,
        val content: String,
        val createdAt: Instant,
        val likeCount: Int,
        val repostCount: Int,
        val replyCount: Int,
        val viewCount: Int,
        val isLikedByCurrentUser: Boolean?,
        val isRepostedByCurrentUser: Boolean?,
    ) : PostRetrievalResult

    data class Failure(
        val exception: Exception,
    ) : PostRetrievalResult
}

sealed interface PostDeletionResult {
    data object Success : PostDeletionResult

    data class Failure(
        val exception: Exception,
    ) : PostDeletionResult
}

class PostValidationException(
    message: String,
) : Exception(message)

class PostNotFoundException(
    message: String,
) : Exception(message)

class PostUserNotFoundException(
    message: String,
) : Exception(message)

class PostDeletionForbiddenException(
    message: String,
) : Exception(message)

@Service
class PostService(
    private val postEventRepository: PostEventRepository,
    private val userRepository: com.example.auth.UserRepository,
    private val likeEventRepository: com.example.like.LikeEventRepository,
    private val objectMapper: ObjectMapper,
) {
    @WithSpan
    fun createPost(
        userId: UUID,
        rawContent: String,
    ): PostCreationResult {
        val validatedContent =
            parsePostContent(rawContent)
                ?: return PostCreationResult.Failure(PostValidationException("Content is invalid"))

        if (!userRepository.existsById(userId)) {
            return PostCreationResult.Failure(PostUserNotFoundException("User not found"))
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
                eventType = PostEventType.POST_CREATED.value,
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
            PostCreationResult.Failure(e)
        }
    }

    @WithSpan
    fun getPost(
        postId: UUID,
        currentUserId: UUID?,
    ): PostRetrievalResult {
        val events =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return PostRetrievalResult.Failure(e)
            }

        val aggregatedPost =
            aggregatePostEvents(events, objectMapper)
                ?: return PostRetrievalResult.Failure(PostNotFoundException("Post not found"))

        val likeEvents =
            try {
                likeEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return PostRetrievalResult.Failure(e)
            }

        val aggregatedLikes = com.example.like.aggregateLikeEvents(likeEvents)
        val likeCount = aggregatedLikes.likeCount
        val isLikedByCurrentUser = currentUserId?.let { it in aggregatedLikes.likedUserIds }

        return PostRetrievalResult.Success(
            postId = postId,
            userId = aggregatedPost.userId,
            content = aggregatedPost.content,
            createdAt = aggregatedPost.createdAt,
            likeCount = likeCount,
            repostCount = 0,
            replyCount = 0,
            viewCount = 0,
            isLikedByCurrentUser = isLikedByCurrentUser,
            isRepostedByCurrentUser = currentUserId?.let { false },
        )
    }

    @WithSpan
    fun deletePost(
        postId: UUID,
        userId: UUID,
    ): PostDeletionResult {
        if (!userRepository.existsById(userId)) {
            return PostDeletionResult.Failure(PostUserNotFoundException("User not found"))
        }

        val events =
            try {
                postEventRepository.findByPostIdOrderByOccurredAtAsc(postId)
            } catch (e: DataAccessException) {
                return PostDeletionResult.Failure(e)
            }

        val aggregatedPost =
            aggregatePostEvents(events, objectMapper)
                ?: return PostDeletionResult.Failure(PostNotFoundException("Post not found"))

        if (aggregatedPost.userId != userId) {
            return PostDeletionResult.Failure(PostDeletionForbiddenException("User is not the post author"))
        }

        return try {
            postEventRepository.save(
                PostEvent(
                    eventId = UUID.randomUUID(),
                    postId = postId,
                    eventType = PostEventType.POST_DELETED.value,
                    eventData = objectMapper.writeValueAsString(mapOf("userId" to userId.toString())),
                    occurredAt = Instant.now(),
                ),
            )
            PostDeletionResult.Success
        } catch (e: DataAccessException) {
            PostDeletionResult.Failure(e)
        }
    }
}
