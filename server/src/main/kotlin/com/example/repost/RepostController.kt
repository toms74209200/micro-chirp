package com.example.repost

import com.example.api.RepostsApi
import com.example.model.DeleteRepostsRequest
import com.example.model.PostReposts201Response
import com.example.model.PostRepostsRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class RepostController(
    private val repostService: RepostService,
) : RepostsApi {
    private val logger = LoggerFactory.getLogger(RepostController::class.java)

    override fun postReposts(
        postId: UUID,
        postRepostsRequest: PostRepostsRequest,
    ): ResponseEntity<PostReposts201Response> =
        when (val result = repostService.repostPost(postId, postRepostsRequest.userId)) {
            is RepostResult.Success -> {
                val response =
                    PostReposts201Response(
                        postId = result.postId,
                        userId = result.userId,
                        repostedAt = OffsetDateTime.ofInstant(result.repostedAt, ZoneOffset.UTC),
                    )
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
            is RepostResult.PostNotFound -> {
                throw RepostPostNotFoundException("Post not found")
            }
            is RepostResult.DataAccessFailure -> {
                throw result.exception
            }
        }

    override fun deleteReposts(
        postId: UUID,
        deleteRepostsRequest: DeleteRepostsRequest,
    ): ResponseEntity<Unit> =
        when (val result = repostService.unrepostPost(postId, deleteRepostsRequest.userId)) {
            is UnrepostResult.Success -> {
                ResponseEntity.noContent().build()
            }
            is UnrepostResult.PostNotFound -> {
                throw RepostPostNotFoundException("Post not found")
            }
            is UnrepostResult.DataAccessFailure -> {
                throw result.exception
            }
        }

    @ExceptionHandler(RepostPostNotFoundException::class)
    fun handleRepostPostNotFoundException(e: RepostPostNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("Post not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
