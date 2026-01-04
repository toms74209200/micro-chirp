package com.example.post

import com.example.api.PostsApi
import com.example.model.PostPosts201Response
import com.example.model.PostPosts400Response
import com.example.model.PostPostsRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset

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
            is PostCreationResult.ValidationFailure -> {
                throw ValidationException(result.errorMessage)
            }
            is PostCreationResult.DataAccessFailure -> {
                throw result.exception
            }
        }

    @ExceptionHandler(ValidationException::class)
    fun handleValidationException(e: ValidationException): ResponseEntity<PostPosts400Response> {
        logger.info("Validation error: {}", e.message)
        val response = PostPosts400Response(error = e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response)
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
