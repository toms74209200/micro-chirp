package com.example.view

import com.example.api.ViewsApi
import com.example.model.PostViews201Response
import com.example.model.PostViewsRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class ViewController(
    private val viewService: ViewService,
) : ViewsApi {
    private val logger = LoggerFactory.getLogger(ViewController::class.java)

    override fun postViews(
        postId: UUID,
        postViewsRequest: PostViewsRequest,
    ): ResponseEntity<PostViews201Response> =
        when (val result = viewService.recordView(postId, postViewsRequest.userId)) {
            is ViewResult.Success -> {
                val response =
                    PostViews201Response(
                        postId = result.postId,
                        userId = result.userId,
                        viewedAt = OffsetDateTime.ofInstant(result.viewedAt, ZoneOffset.UTC),
                    )
                ResponseEntity.status(HttpStatus.CREATED).body(response)
            }
            is ViewResult.Failure -> {
                throw result.exception
            }
        }

    @ExceptionHandler(ViewUserNotFoundException::class)
    fun handleViewUserNotFoundException(e: ViewUserNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("User not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(ViewPostNotFoundException::class)
    fun handleViewPostNotFoundException(e: ViewPostNotFoundException): ResponseEntity<Map<String, String>> {
        logger.info("Post not found: {}", e.message)
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(mapOf("error" to (e.message ?: "")))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }
}
