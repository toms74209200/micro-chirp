package com.example.like

import com.example.api.LikesApi
import com.example.model.DeleteLikesRequest
import com.example.model.PostLikes201Response
import com.example.model.PostLikesRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class LikeController(
    private val likeService: LikeService,
) : LikesApi {
    private val logger = LoggerFactory.getLogger(LikeController::class.java)

    override fun postLikes(
        postId: UUID,
        postLikesRequest: PostLikesRequest,
    ): ResponseEntity<PostLikes201Response> =
        when (val result = likeService.likePost(postId, postLikesRequest.userId)) {
            is LikeResult.Success -> {
                val response =
                    PostLikes201Response(
                        postId = result.postId,
                        userId = result.userId,
                        likedAt = OffsetDateTime.ofInstant(result.likedAt, ZoneOffset.UTC),
                    )
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
            is LikeResult.Failure -> {
                throw result.exception
            }
        }

    override fun deleteLikes(
        postId: UUID,
        deleteLikesRequest: DeleteLikesRequest,
    ): ResponseEntity<Unit> =
        when (val result = likeService.unlikePost(postId, deleteLikesRequest.userId)) {
            is UnlikeResult.Success -> {
                ResponseEntity.noContent().build()
            }
            is UnlikeResult.Failure -> {
                throw result.exception
            }
        }

    @ExceptionHandler(LikeUserNotFoundException::class)
    fun handleLikeUserNotFoundException(e: LikeUserNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("User not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(LikePostNotFoundException::class)
    fun handleLikePostNotFoundException(e: LikePostNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("Post not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
