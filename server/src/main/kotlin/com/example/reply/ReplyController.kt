package com.example.reply

import com.example.api.RepliesApi
import com.example.model.GetReplies200Response
import com.example.model.PostReplies201Response
import com.example.model.PostRepliesRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class ReplyController(
    private val replyService: ReplyService,
) : RepliesApi {
    private val logger = LoggerFactory.getLogger(ReplyController::class.java)

    override fun postReplies(
        postId: UUID,
        postRepliesRequest: PostRepliesRequest,
    ): ResponseEntity<PostReplies201Response> =
        when (val result = replyService.replyToPost(postId, postRepliesRequest.userId, postRepliesRequest.content)) {
            is ReplyCreationResult.Success -> {
                val response =
                    PostReplies201Response(
                        replyPostId = result.replyPostId,
                        replyToPostId = result.replyToPostId,
                        userId = result.userId,
                        content = result.content,
                        createdAt = OffsetDateTime.ofInstant(result.createdAt, ZoneOffset.UTC),
                    )
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
            is ReplyCreationResult.PostNotFound -> {
                throw ReplyPostNotFoundException("Post not found")
            }
            is ReplyCreationResult.ValidationFailure -> {
                throw ReplyValidationException(result.errorMessage)
            }
            is ReplyCreationResult.DataAccessFailure -> {
                throw result.exception
            }
        }

    override fun getReplies(
        postId: UUID,
        limit: Int,
        offset: Int,
        userId: UUID?,
    ): ResponseEntity<GetReplies200Response> = TODO("getReplies is not yet implemented")

    @ExceptionHandler(ReplyPostNotFoundException::class)
    fun handleReplyPostNotFoundException(e: ReplyPostNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("Post not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(ReplyValidationException::class)
    fun handleReplyValidationException(e: ReplyValidationException): ResponseEntity<Map<String, String>> {
        logger.info("Validation failed: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
