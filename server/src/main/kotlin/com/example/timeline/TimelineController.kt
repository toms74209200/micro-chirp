package com.example.timeline

import com.example.api.TimelineApi
import com.example.model.GetPostsById200Response
import com.example.model.GetTimelineGlobal200Response
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

@RestController
class TimelineController(
    private val timelineService: TimelineService,
) : TimelineApi {
    private val logger = LoggerFactory.getLogger(TimelineController::class.java)

    override fun getTimelineGlobal(
        limit: Int,
        afterPostId: UUID?,
        userId: UUID?,
    ): ResponseEntity<GetTimelineGlobal200Response> =
        when (val result = timelineService.getGlobalTimeline(limit, afterPostId, userId)) {
            is TimelineResult.Success -> {
                ResponseEntity.ok(
                    GetTimelineGlobal200Response(
                        posts = result.posts.map { it.toResponse() },
                        limit = result.limit,
                    ),
                )
            }
            is TimelineResult.Failure -> throw result.exception
        }

    override fun getTimelineByUserId(
        userId: UUID,
        limit: Int,
        afterPostId: UUID?,
        currentUserId: UUID?,
    ): ResponseEntity<GetTimelineGlobal200Response> = TODO()

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<Void> {
        logger.warn("An unexpected error occurred", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build()
    }

    private fun TimelineResult.PostItem.toResponse() =
        GetPostsById200Response(
            postId = postId,
            userId = userId,
            content = content,
            createdAt = OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC),
            likeCount = likeCount,
            repostCount = repostCount,
            replyCount = replyCount,
            viewCount = viewCount,
            isLikedByCurrentUser = isLikedByCurrentUser,
            isRepostedByCurrentUser = isRepostedByCurrentUser,
        )
}
