package com.example.post

import com.example.api.PostsApi
import com.example.model.GetPostsById200Response
import com.example.model.PostPosts201Response
import com.example.model.PostPostsRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class PostController(
    private val postService: PostService,
) : PostsApi {
    private val logger = LoggerFactory.getLogger(PostController::class.java)

    override fun postPosts(postPostsRequest: PostPostsRequest): ResponseEntity<PostPosts201Response> =
        when (val result = postService.createPost(postPostsRequest.userId, postPostsRequest.content)) {
            is PostCreationResult.Success -> {
                val response =
                    PostPosts201Response(
                        postId = result.postId,
                        userId = result.userId,
                        content = result.content,
                        createdAt = OffsetDateTime.ofInstant(result.createdAt, ZoneOffset.UTC),
                    )
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
            is PostCreationResult.Failure -> {
                throw result.exception
            }
        }

    override fun getPostsById(
        postId: UUID,
        userId: UUID?,
    ): ResponseEntity<GetPostsById200Response> =
        when (val result = postService.getPost(postId, userId)) {
            is PostRetrievalResult.Success -> {
                val response =
                    GetPostsById200Response(
                        postId = result.postId,
                        userId = result.userId,
                        content = result.content,
                        createdAt = OffsetDateTime.ofInstant(result.createdAt, ZoneOffset.UTC),
                        likeCount = result.likeCount,
                        repostCount = result.repostCount,
                        replyCount = result.replyCount,
                        viewCount = result.viewCount,
                        isLikedByCurrentUser = result.isLikedByCurrentUser,
                        isRepostedByCurrentUser = result.isRepostedByCurrentUser,
                    )
                ResponseEntity.ok(response)
            }
            is PostRetrievalResult.Failure -> {
                throw result.exception
            }
        }

    override fun deletePostsById(
        postId: UUID,
        deletePostsByIdRequest: com.example.model.DeletePostsByIdRequest,
    ): ResponseEntity<Unit> =
        when (val result = postService.deletePost(postId, deletePostsByIdRequest.userId)) {
            is PostDeletionResult.Success -> {
                ResponseEntity.noContent().build()
            }
            is PostDeletionResult.Failure -> {
                throw result.exception
            }
        }

    @ExceptionHandler(PostValidationException::class)
    fun handlePostValidationException(e: PostValidationException): ResponseEntity<Map<String, String>> {
        logger.info("Validation error: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(PostUserNotFoundException::class)
    fun handlePostUserNotFoundException(e: PostUserNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("User not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(PostNotFoundException::class)
    fun handlePostNotFoundException(e: PostNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("Post not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(PostDeletionForbiddenException::class)
    fun handlePostDeletionForbiddenException(e: PostDeletionForbiddenException): ResponseEntity<Map<String, String>> {
        logger.info("Post deletion forbidden: {}", e.message)
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
